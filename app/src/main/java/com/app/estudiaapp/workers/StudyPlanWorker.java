package com.app.estudiaapp.workers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.app.estudiaapp.utils.DocumentExtractor;
import com.app.estudiaapp.utils.OpenAiClient;
import com.app.estudiaapp.utils.OpenAiKeyStore;
import com.app.estudiaapp.utils.RetryUtils;
import com.app.estudiaapp.utils.TokenUtils;
import com.google.firebase.Timestamp;
import com.google.gson.JsonObject;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.google.firebase.storage.UploadTask;

public class StudyPlanWorker extends Worker {
    private static final String TAG = "StudyPlanWorker";

    public static final String INPUT_URI = "input_uri";
    public static final String INPUT_TITLE = "input_title";
    public static final String INPUT_INCLUDE_QUIZZES = "input_include_quizzes";
    public static final String INPUT_QUIZ_LIMIT = "input_quiz_limit";
    public static final String INPUT_QUESTIONS_LIMIT = "input_questions_limit";
    public static final String INPUT_INCLUDE_FLASHCARDS = "input_include_flashcards";
    public static final String INPUT_FLASHCARD_LIMIT = "input_flashcard_limit";
    public static final String INPUT_INCLUDE_HOT_TOPICS = "input_include_hot_topics";

    public StudyPlanWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private Result failureWithMessage(String msg) {
        try {
            Data d = new Data.Builder().putString("error", msg).build();
            return Result.failure(d);
        } catch (Exception e) {
            return Result.failure();
        }
    }
    /**
     * Allocates a canonical PlanId for the given user using a Firestore transaction.
     * The id format is U0##### where the numeric part is a per-user incrementing counter.
     */
    private String allocateCanonicalPlanId(FirebaseFirestore db, String uid) throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Exception> exRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            final DocumentReference userRef = db.collection("users").document(uid);
            db.runTransaction((Transaction.Function<Void>) transaction -> {
                DocumentSnapshot snap = transaction.get(userRef);
                long current = 0L;
                if (snap != null && snap.exists() && snap.contains("nextPlanNumber")) {
                    try {
                        Object v = snap.get("nextPlanNumber");
                        if (v instanceof Number) current = ((Number) v).longValue();
                        else current = Long.parseLong(String.valueOf(v));
                    } catch (Exception ignored) {
                        current = 0L;
                    }
                }
                long next = current + 1L;
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("nextPlanNumber", next);
                transaction.set(userRef, updates, SetOptions.merge());
                String planId = String.format("U0%05d", next);
                result.set(planId);
                return null;
            }).addOnSuccessListener(aVoid -> latch.countDown()).addOnFailureListener(e -> { exRef.set(e); latch.countDown(); });

            // wait but remain responsive to cancellation
            while (latch.getCount() > 0) {
                if (isStopped()) {
                    throw new InterruptedException("Worker stopped while allocating planId");
                }
                latch.await(200, TimeUnit.MILLISECONDS);
            }

            if (exRef.get() != null) throw new RuntimeException(exRef.get());
            return result.get();
        } catch (InterruptedException ie) {
            throw ie;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        String uriStr = getInputData().getString(INPUT_URI);
        if (uriStr == null || uriStr.isEmpty()) {
            Log.w(TAG, "No input URI provided");
            return failureWithMessage("No input URI provided");
        }

        Uri uri = Uri.parse(uriStr);
        try {
            setProgressAsync(new Data.Builder().putString("status", "extracting").build());
            String extracted = DocumentExtractor.extractTextFromUri(getApplicationContext(), uri);
            if (extracted == null) extracted = "";

            // Resolve API key from local store (UploadActivity stores RemoteConfig fetched key there)
            String apiKey = OpenAiKeyStore.getKey(getApplicationContext());
            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "OpenAI key missing in worker");
                return failureWithMessage("OpenAI API key not available");
            }

            // If text is large, do chunk summarization
            int est = TokenUtils.estimateTokens(extracted);
            boolean needsChunking = TokenUtils.needsChunking(extracted, 128000);
            if (needsChunking) {
                setProgressAsync(new Data.Builder().putString("status", "summarizing").build());
                OpenAiClient client = new OpenAiClient();
                int chunkSize = 30000;
                List<String> chunks = new ArrayList<>();
                for (int i = 0; i < extracted.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, extracted.length());
                    chunks.add(extracted.substring(i, end));
                }
                List<String> summaries = new ArrayList<>();
                for (String ch : chunks) {
                    if (isStopped()) {
                        Log.i(TAG, "Work stopped during chunk summarization");
                        return failureWithMessage("Cancelled");
                    }
                    try {
                        String summary = RetryUtils.runWithRetries(() -> client.summarizeChunk(apiKey, ch), 2, 500L, (Exception ex) -> {
                            if (ex instanceof com.app.estudiaapp.utils.OpenAiException) {
                                return ((com.app.estudiaapp.utils.OpenAiException) ex).isRetryable();
                            }
                            return true;
                        });
                        if (summary == null || summary.trim().isEmpty()) summary = ch.length() > 1000 ? ch.substring(0, 1000) : ch;
                        summaries.add(summary);
                    } catch (Exception e) {
                        Log.w(TAG, "Chunk summarization failed: " + e.getMessage());
                        summaries.add(ch.length() > 1000 ? ch.substring(0, 1000) : ch);
                    }
                }
                StringBuilder combined = new StringBuilder();
                for (String s : summaries) combined.append(s).append('\n').append('\n');
                extracted = combined.toString();
            }

            // Parse worker input options (form data) early so we can include them in the schema request
            String inputTitle = getInputData().getString(INPUT_TITLE);
            boolean includeQuizzes = getInputData().getBoolean(INPUT_INCLUDE_QUIZZES, false);
            int quizLimit = getInputData().getInt(INPUT_QUIZ_LIMIT, 0);
            int questionsLimit = getInputData().getInt(INPUT_QUESTIONS_LIMIT, 0);
            boolean includeFlashcards = getInputData().getBoolean(INPUT_INCLUDE_FLASHCARDS, false);
            int flashcardLimit = getInputData().getInt(INPUT_FLASHCARD_LIMIT, 0);
            boolean includeHotTopics = getInputData().getBoolean(INPUT_INCLUDE_HOT_TOPICS, false);

            setProgressAsync(new Data.Builder().putString("status", "calling_ai").build());
            OpenAiClient client = new OpenAiClient();
            final AtomicReference<String> aiResponse = new AtomicReference<>();
            try {
                final String finalExtracted = extracted; // lambda requires effectively final variable

                // Build a minimal JSON Schema for the StudyPlan
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                JsonObject props = new JsonObject();

                JsonObject idS = new JsonObject(); idS.addProperty("type", "string"); props.add("id", idS);
                JsonObject nombreS = new JsonObject(); nombreS.addProperty("type", "string"); props.add("nombre", nombreS);

                // flashcards
                JsonObject flashcardsS = new JsonObject(); flashcardsS.addProperty("type", "array");
                JsonObject flashcardItem = new JsonObject(); flashcardItem.addProperty("type", "object");
                JsonObject fcProps = new JsonObject(); JsonObject p1 = new JsonObject(); p1.addProperty("type", "boolean"); fcProps.add("completada", p1);
                JsonObject p2 = new JsonObject(); p2.addProperty("type", "string"); fcProps.add("pregunta", p2);
                JsonObject p3 = new JsonObject(); p3.addProperty("type", "string"); fcProps.add("respuesta", p3);
                flashcardItem.add("properties", fcProps);
                flashcardItem.add("required", new com.google.gson.JsonArray());
                flashcardsS.add("items", flashcardItem);
                props.add("flashcards", flashcardsS);

                // hotTopics
                JsonObject htS = new JsonObject(); htS.addProperty("type", "array");
                JsonObject htItem = new JsonObject(); htItem.addProperty("type", "object");
                JsonObject htProps = new JsonObject(); JsonObject htTitle = new JsonObject(); htTitle.addProperty("type", "string"); htProps.add("title", htTitle);
                JsonObject htDesc = new JsonObject(); htDesc.addProperty("type", "string"); htProps.add("description", htDesc);
                htItem.add("properties", htProps);
                htItem.add("required", new com.google.gson.JsonArray());
                htS.add("items", htItem);
                props.add("hotTopics", htS);

                // progreso
                JsonObject progS = new JsonObject(); progS.addProperty("type", "object");
                JsonObject progProps = new JsonObject(); JsonObject estProp = new JsonObject(); estProp.addProperty("type", "number"); progProps.add("estimatedStudyTimeMinutes", estProp);
                JsonObject fe = new JsonObject(); fe.addProperty("type", "number"); progProps.add("flashcardsEstudiadas", fe);
                JsonObject niv = new JsonObject(); niv.addProperty("type", "number"); progProps.add("nivel", niv);
                progS.add("properties", progProps);
                props.add("progreso", progS);

                // quizzes
                JsonObject qzS = new JsonObject(); qzS.addProperty("type", "array");
                JsonObject qzItem = new JsonObject(); qzItem.addProperty("type", "object");
                JsonObject qzProps = new JsonObject(); JsonObject qid = new JsonObject(); qid.addProperty("type", "string"); qzProps.add("id", qid);
                JsonObject qtitle = new JsonObject(); qtitle.addProperty("type", "string"); qzProps.add("titulo", qtitle);
                JsonObject questions = new JsonObject(); questions.addProperty("type", "array"); JsonObject qItem = new JsonObject(); qItem.addProperty("type", "object"); JsonObject qItemProps = new JsonObject(); JsonObject qQuestion = new JsonObject(); qQuestion.addProperty("type", "string"); qItemProps.add("question", qQuestion); JsonObject qAnswer = new JsonObject(); qAnswer.addProperty("type", "string"); qItemProps.add("answer", qAnswer); qItem.add("properties", qItemProps); questions.add("items", qItem);
                qzProps.add("questions", questions);
                qzItem.add("properties", qzProps);
                qzS.add("items", qzItem);
                props.add("quizzes", qzS);

                schema.add("properties", props);
                com.google.gson.JsonArray req = new com.google.gson.JsonArray(); req.add(new com.google.gson.JsonPrimitive("id")); req.add(new com.google.gson.JsonPrimitive("nombre")); req.add(new com.google.gson.JsonPrimitive("flashcards")); req.add(new com.google.gson.JsonPrimitive("hotTopics")); req.add(new com.google.gson.JsonPrimitive("progreso")); req.add(new com.google.gson.JsonPrimitive("quizzes"));
                schema.add("required", req);

                // Perform structured request using Responses API
                int useQuizLimit = quizLimit > 0 ? quizLimit : 3;
                int useQuestionsLimit = questionsLimit > 0 ? questionsLimit : 5;
                int useFlashcardLimit = flashcardLimit > 0 ? flashcardLimit : 10;
                int useHotTopicsLimit = 5;
                String resp = RetryUtils.runWithRetries(() -> client.generateStudyPlanWithSchema(apiKey, inputTitle == null ? "" : inputTitle, "", "", useQuizLimit, useQuestionsLimit, useFlashcardLimit, useHotTopicsLimit, finalExtracted, schema), 3, 700L, (Exception ex) -> {
                    if (ex instanceof com.app.estudiaapp.utils.OpenAiException) {
                        return ((com.app.estudiaapp.utils.OpenAiException) ex).isRetryable();
                    }
                    return true;
                });
                aiResponse.set(resp);
            } catch (Exception e) {
                Log.w(TAG, "OpenAI generateStudyPlan failed: " + e.getMessage());
                return failureWithMessage("OpenAI call failed: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
            }

            // Validate response and enforce NO_PLAN_POSSIBLE sentinel
            if (aiResponse.get() == null || aiResponse.get().trim().isEmpty()) {
                Log.w(TAG, "Empty AI response");
                return failureWithMessage("Empty response from AI");
            }
            String rawResp = aiResponse.get().trim();
            if ("NO_PLAN_POSSIBLE".equals(rawResp)) {
                Log.i(TAG, "AI indicated no plan possible for this document");
                return failureWithMessage("NO_PLAN_POSSIBLE");
            }

            // Try to extract first JSON object/array from the Responses API raw body (it may include wrappers)
            String json = extractFirstJson(rawResp);
            if (json == null) {
                // As a fallback, try to find JSON inside an "output" or similar field
                try {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(rawResp).getAsJsonObject();
                    if (root.has("output") && root.get("output").isJsonArray()) {
                        com.google.gson.JsonArray outArr = root.getAsJsonArray("output");
                        for (int i = 0; i < outArr.size(); i++) {
                            com.google.gson.JsonObject o = outArr.get(i).getAsJsonObject();
                            if (o.has("content") && o.get("content").isJsonArray()) {
                                com.google.gson.JsonArray content = o.getAsJsonArray("content");
                                for (int j = 0; j < content.size(); j++) {
                                    try {
                                        String candidate = content.get(j).toString();
                                        String candJson = extractFirstJson(candidate);
                                        if (candJson != null) { json = candJson; break; }
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (json != null) break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (json == null) {
                Log.w(TAG, "Could not extract JSON from AI response");
                return failureWithMessage("Invalid response from AI (no JSON)");
            }

            setProgressAsync(new Data.Builder().putString("status", "uploading").build());

            // Upload source file to Storage
            FirebaseStorage storage = FirebaseStorage.getInstance();
            String uid = "unknown";
            try { uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
            // Allocate a canonical planId using a Firestore transaction (per-user counter)
            String planId = null;
            try {
                planId = allocateCanonicalPlanId(FirebaseFirestore.getInstance(), uid);
            } catch (Exception e) {
                Log.w(TAG, "Failed to allocate canonical planId, falling back to UUID: " + e.getMessage());
            }
            if (planId == null) planId = UUID.randomUUID().toString();
            com.google.firebase.storage.StorageReference ref = storage.getReference().child("users/" + uid + "/plans/" + planId + "/source.pdf");

            final CountDownLatch uploadLatch = new CountDownLatch(1);
            final AtomicReference<com.google.firebase.storage.UploadTask.TaskSnapshot> uploadSnapshotRef = new AtomicReference<>();
            final AtomicReference<Exception> uploadEx = new AtomicReference<>();
            final AtomicReference<UploadTask> uploadTaskRef = new AtomicReference<>();

            UploadTask uploadTask = ref.putFile(uri);
            uploadTaskRef.set(uploadTask);
            uploadTask.addOnSuccessListener(snap -> {
                uploadSnapshotRef.set(snap);
                uploadLatch.countDown();
            }).addOnFailureListener(e -> {
                uploadEx.set(e);
                uploadLatch.countDown();
            });

            // Wait in short intervals so we can notice cancellation
            while (uploadLatch.getCount() > 0) {
                if (isStopped()) {
                    UploadTask ut = uploadTaskRef.get();
                    if (ut != null) {
                        try { ut.cancel(); } catch (Exception ignored) {}
                    }
                    Log.i(TAG, "Worker stopped; cancelled upload task");
                    return failureWithMessage("Cancelled during upload");
                }
                uploadLatch.await(200, TimeUnit.MILLISECONDS);
            }
            if (uploadEx.get() != null) {
                Log.w(TAG, "Upload failed: " + uploadEx.get().getMessage());
                return failureWithMessage("Upload failed: " + (uploadEx.get().getMessage() != null ? uploadEx.get().getMessage() : "unknown"));
            }

            // Save plan JSON to Firestore
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            final CountDownLatch saveLatch = new CountDownLatch(1);
            final AtomicReference<Exception> saveEx = new AtomicReference<>();

            try {
                // Parse AI response and normalize to expected plan schema
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.util.Map parsed = gson.fromJson(json, java.util.Map.class);
                java.util.Map<String, Object> data = normalizePlan(parsed, planId, ref.getPath());
                // Apply form overrides and validate/fill missing sections — try asking OpenAI for missing sections first
                validateAndFillPlan(data, extracted, inputTitle, includeQuizzes, quizLimit, questionsLimit, includeFlashcards, flashcardLimit, includeHotTopics, client, apiKey);
                data.put("createdAt", Timestamp.now());
                db.collection("users").document(uid).collection("plans").document(planId).set(data)
                        .addOnSuccessListener(aVoid -> saveLatch.countDown())
                        .addOnFailureListener(e -> { saveEx.set(e); saveLatch.countDown(); });
                // Wait with cancellation checks
                while (saveLatch.getCount() > 0) {
                    if (isStopped()) {
                        Log.i(TAG, "Worker stopped while saving plan to Firestore");
                        return failureWithMessage("Cancelled while saving plan");
                    }
                    saveLatch.await(200, TimeUnit.MILLISECONDS);
                }
                if (saveEx.get() != null) {
                    Log.w(TAG, "Save plan failed: " + saveEx.get().getMessage());
                    return failureWithMessage("Save plan failed: " + (saveEx.get().getMessage() != null ? saveEx.get().getMessage() : "unknown"));
                }
            } catch (Exception e) {
                Log.w(TAG, "Exception saving plan: " + e.getMessage());
                return failureWithMessage("Exception saving plan: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
            }

            setProgressAsync(new Data.Builder().putString("status", "done").build());
            return Result.success(new Data.Builder().putString("planId", planId).build());

        } catch (Exception e) {
            Log.w(TAG, "Worker failed: " + e.getMessage(), e);
            return failureWithMessage("Worker failed: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
        }
    }

    // -- Helper methods to normalize plan structure --
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> normalizePlan(java.util.Map parsed, String planId, String sourcePath) {
        if (parsed == null) parsed = new java.util.HashMap();
        java.util.Map<String, Object> out = new java.util.HashMap<>();

        out.put("id", planId);
        // nombre: try several keys
        String nombre = pickString(parsed, "nombre", "title", "name");
        if (nombre == null || nombre.trim().isEmpty()) nombre = planId;
        out.put("nombre", nombre);

        // flashcards: normalize list of maps with pregunta/respuesta/completada
        Object fcObj = parsed.get("flashcards");
        java.util.List<java.util.Map<String, Object>> flashcards = new java.util.ArrayList<>();
        if (fcObj instanceof java.util.List) {
            java.util.List list = (java.util.List) fcObj;
            for (int i = 0; i < list.size(); i++) {
                Object el = list.get(i);
                java.util.Map<String, Object> fm = new java.util.HashMap<>();
                String pregunta = null;
                String respuesta = null;
                boolean completada = false;
                if (el instanceof java.util.Map) {
                    java.util.Map m = (java.util.Map) el;
                    pregunta = pickString(m, "pregunta", "question", "q");
                    respuesta = pickString(m, "respuesta", "answer", "a");
                    Object c = m.get("completada");
                    if (c instanceof Boolean) completada = (Boolean) c;
                }
                if (pregunta == null) pregunta = String.format("Ficha %d: (sin pregunta)", i + 1);
                if (respuesta == null) respuesta = "(sin respuesta)";
                fm.put("pregunta", pregunta);
                fm.put("respuesta", respuesta);
                fm.put("completada", completada);
                flashcards.add(fm);
            }
        }
        out.put("flashcards", flashcards);

        // hotTopics: normalize
        Object htObj = parsed.get("hotTopics");
        java.util.List<java.util.Map<String, Object>> hotTopics = new java.util.ArrayList<>();
        if (htObj instanceof java.util.List) {
            java.util.List list = (java.util.List) htObj;
            for (Object el : list) {
                if (el instanceof java.util.Map) {
                    java.util.Map m = (java.util.Map) el;
                    java.util.Map<String, Object> hm = new java.util.HashMap<>();
                    String title = pickString(m, "title", "titulo", "name");
                    String desc = pickString(m, "description", "descripcion", "desc");
                    if (title == null) title = "(sin titulo)";
                    if (desc == null) desc = "";
                    hm.put("title", title);
                    hm.put("description", desc);
                    hotTopics.add(hm);
                }
            }
        }
        out.put("hotTopics", hotTopics);

        // quizzes: normalize
        Object qzObj = parsed.get("quizzes");
        java.util.List<java.util.Map<String, Object>> quizzes = new java.util.ArrayList<>();
        if (qzObj instanceof java.util.List) {
            java.util.List qlist = (java.util.List) qzObj;
            for (int qi = 0; qi < qlist.size(); qi++) {
                Object qel = qlist.get(qi);
                java.util.Map<String, Object> qout = new java.util.HashMap<>();
                String qid = null;
                String titulo = null;
                java.util.Map<String, Object> progreso = new java.util.HashMap<>();
                java.util.List<java.util.Map<String, Object>> questions = new java.util.ArrayList<>();
                if (qel instanceof java.util.Map) {
                    java.util.Map qm = (java.util.Map) qel;
                    qid = pickString(qm, "id");
                    titulo = pickString(qm, "titulo", "title", "name");
                    Object prog = qm.get("progreso");
                    if (prog instanceof java.util.Map) {
                        java.util.Map pm = (java.util.Map) prog;
                        progreso.put("currentIndex", safeInt(pm.get("currentIndex"), 0));
                        progreso.put("lastUpdatedMillis", safeLong(pm.get("lastUpdatedMillis"), 0L));
                        progreso.put("nota", safeInt(pm.get("nota"), -1));
                        progreso.put("preguntasCorrectas", safeInt(pm.get("preguntasCorrectas"), 0));
                        progreso.put("preguntasIncorrectas", safeInt(pm.get("preguntasIncorrectas"), 0));
                        progreso.put("preguntasRespondidas", safeInt(pm.get("preguntasRespondidas"), 0));
                    } else {
                        progreso.put("currentIndex", 0);
                        progreso.put("lastUpdatedMillis", 0L);
                        progreso.put("nota", -1);
                        progreso.put("preguntasCorrectas", 0);
                        progreso.put("preguntasIncorrectas", 0);
                        progreso.put("preguntasRespondidas", 0);
                    }
                    Object qarr = qm.get("questions");
                    if (qarr instanceof java.util.List) {
                        java.util.List qarrList = (java.util.List) qarr;
                        for (Object qo : qarrList) {
                            java.util.Map<String, Object> qmap = new java.util.HashMap<>();
                            if (qo instanceof java.util.Map) {
                                java.util.Map qom = (java.util.Map) qo;
                                String question = pickString(qom, "question", "pregunta");
                                String answer = pickString(qom, "answer", "respuesta");
                                java.util.List options = new java.util.ArrayList();
                                Object opts = qom.get("options");
                                if (opts instanceof java.util.List) options.addAll((java.util.List) opts);
                                qmap.put("question", question != null ? question : "(sin pregunta)");
                                qmap.put("options", options);
                                qmap.put("answer", answer != null ? answer : (options.size() > 0 ? options.get(0) : ""));
                            }
                            questions.add(qmap);
                        }
                    }
                }
                if (qid == null) qid = String.format("qz%d", qi);
                if (titulo == null) titulo = "Quiz " + (qi + 1);
                qout.put("id", qid);
                qout.put("titulo", titulo);
                qout.put("progreso", progreso);
                qout.put("questions", questions);
                quizzes.add(qout);
            }
        }
        out.put("quizzes", quizzes);

        // progreso (global)
        java.util.Map<String, Object> progresoGlobal = new java.util.HashMap<>();
        progresoGlobal.put("estimatedStudyTimeMinutes", safeInt(parsed.get("estimatedStudyTimeMinutes"), 0));
        progresoGlobal.put("flashcardsEstudiadas", safeInt(parsed.get("flashcardsEstudiadas"), 0));
        progresoGlobal.put("nivel", safeInt(parsed.get("nivel"), 1));
        progresoGlobal.put("preguntasCorrectas", safeInt(parsed.get("preguntasCorrectas"), 0));
        progresoGlobal.put("preguntasRespondidas", safeInt(parsed.get("preguntasRespondidas"), 0));
        out.put("progreso", progresoGlobal);

        // Convenience top-level numeric fields
        out.put("estimatedStudyTimeMinutes", safeInt(parsed.get("estimatedStudyTimeMinutes"), 0));
        out.put("flashcardsEstudiadas", safeInt(parsed.get("flashcardsEstudiadas"), 0));
        out.put("nivel", safeInt(parsed.get("nivel"), 1));

        out.put("sourcePath", sourcePath);

        return out;
    }

    private static String pickString(java.util.Map m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            try {
                Object v = m.get(k);
                if (v instanceof String) return (String) v;
                if (v != null) return String.valueOf(v);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static int safeInt(Object o, int dflt) {
        try {
            if (o instanceof Number) return ((Number) o).intValue();
            if (o instanceof String) return Integer.parseInt((String) o);
        } catch (Exception ignored) {}
        return dflt;
    }

    private static long safeLong(Object o, long dflt) {
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            if (o instanceof String) return Long.parseLong((String) o);
        } catch (Exception ignored) {}
        return dflt;
    }

    /** Validate that the normalized plan contains required sections; if missing, try to fill them from the extracted text
     *  - inputTitle overrides plan title if provided
     *  - if includeFlashcards==true and flashcards size < flashcardLimit, generate placeholders
     *  - if includeQuizzes==true and quizzes size < quizLimit, generate simple quizzes with questionsLimit questions
     *  - if includeHotTopics==true and hotTopics empty, generate simple hot topics from top sentences
     */
    private void validateAndFillPlan(java.util.Map<String, Object> plan, String extractedText, String inputTitle,
                                     boolean includeQuizzes, int quizLimit, int questionsLimit,
                                     boolean includeFlashcards, int flashcardLimit,
                                     boolean includeHotTopics, OpenAiClient client, String apiKey) {
        if (plan == null) return;
        try {
            // Title override
            if (inputTitle != null && !inputTitle.trim().isEmpty()) plan.put("nombre", inputTitle.trim());

            // Flashcards
            java.util.List flashcards = (java.util.List) plan.get("flashcards");
            if (flashcards == null) flashcards = new java.util.ArrayList();
            // If user didn't provide limits, set sensible defaults
            if (flashcardLimit <= 0) flashcardLimit = 10;
            // Try to generate flashcards if requested OR if plan currently has none
            if ((includeFlashcards || flashcards.isEmpty()) && flashcards.size() < flashcardLimit) {
                // Try to ask OpenAI specifically for flashcards if none or insufficient
                if (client != null && apiKey != null && !apiKey.isEmpty()) {
                    try {
                        String instr = "You MUST respond with ONLY a JSON array (no explanation, no markdown).\n" +
                                "Return up to " + flashcardLimit + " flashcards extracted from the DOCUMENT.\n" +
                                "Each flashcard must be an object with exactly these keys: \"pregunta\" (string) and \"respuesta\" (string).\n" +
                                "Example response:\n" +
                                "[ {\"pregunta\": \"¿Qué es X?\", \"respuesta\": \"X es ...\"} ]\n\n" +
                                "DOCUMENT:\n" + extractedText;
                        String resp = RetryUtils.runWithRetries(() -> client.generateJsonForSection(apiKey, instr, 600), 2, 500L, (Exception ex) -> {
                            if (ex instanceof com.app.estudiaapp.utils.OpenAiException) return ((com.app.estudiaapp.utils.OpenAiException) ex).isRetryable();
                            return true;
                        });
                        String json = extractFirstJson(resp);
                        if (json != null) {
                            try {
                                com.google.gson.Gson gg = new com.google.gson.Gson();
                                java.util.List parsedFc = gg.fromJson(json, java.util.List.class);
                                if (parsedFc != null && parsedFc.size() > 0) {
                                    flashcards.clear();
                                    for (Object o : parsedFc) flashcards.add(o);
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "OpenAI flashcards request failed: " + e.getMessage());
                    }
                }
                int needed = Math.max(0, flashcardLimit - flashcards.size());
                java.util.List<String> snippets = extractTopSnippets(extractedText, needed);
                for (int i = 0; i < needed; i++) {
                    java.util.Map<String,Object> fm = new java.util.HashMap<>();
                    String snippet = i < snippets.size() ? snippets.get(i) : null;
                    String q;
                    String a;
                    if (snippet != null) {
                        String shortSnippet = snippet.length() > 120 ? snippet.substring(0, 120) + "..." : snippet;
                        q = String.format("Explica brevemente: %s", shortSnippet);
                        a = snippet;
                    } else {
                        q = String.format("Ficha %d: Concepto clave", flashcards.size() + 1);
                        a = "(detalles sobre el concepto)";
                    }
                    fm.put("pregunta", q);
                    fm.put("respuesta", a);
                    fm.put("completada", false);
                    flashcards.add(fm);
                }
            }
            plan.put("flashcards", flashcards);

            // Hot topics
            java.util.List hotTopics = (java.util.List) plan.get("hotTopics");
            if (hotTopics == null) hotTopics = new java.util.ArrayList();
            // Always attempt to populate hotTopics if none present (or explicitly requested)
            if ((includeHotTopics || hotTopics.isEmpty()) && hotTopics.isEmpty()) {
                if (client != null && apiKey != null && !apiKey.isEmpty()) {
                    try {
                        String instr = "You MUST respond with ONLY a JSON array (no explanation, no markdown).\n" +
                                "Return up to 5 hot topics extracted from the DOCUMENT.\n" +
                                "Each element must be an object with keys: \"title\" (short string) and \"description\" (longer string).\n" +
                                "Example response:\n" +
                                "[ {\"title\": \"Tema A\", \"description\": \"Descripción breve del tema A...\"} ]\n\n" +
                                "DOCUMENT:\n" + extractedText;
                        String resp = RetryUtils.runWithRetries(() -> client.generateJsonForSection(apiKey, instr, 400), 2, 500L, (Exception ex) -> {
                            if (ex instanceof com.app.estudiaapp.utils.OpenAiException) return ((com.app.estudiaapp.utils.OpenAiException) ex).isRetryable();
                            return true;
                        });
                        String json = extractFirstJson(resp);
                        if (json != null) {
                            try {
                                com.google.gson.Gson gg = new com.google.gson.Gson();
                                java.util.List parsedHt = gg.fromJson(json, java.util.List.class);
                                if (parsedHt != null && parsedHt.size() > 0) {
                                    hotTopics.clear();
                                    for (Object o : parsedHt) hotTopics.add(o);
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "OpenAI hotTopics request failed: " + e.getMessage());
                    }
                }
                java.util.List<String> snippets = extractTopSnippets(extractedText, 5);
                for (int i = 0; i < Math.min(5, snippets.size()); i++) {
                    java.util.Map<String,Object> hm = new java.util.HashMap<>();
                    String s = snippets.get(i);
                    String title = s.split("\\.")[0];
                    if (title.length() > 60) title = title.substring(0, 60) + "...";
                    hm.put("title", title);
                    hm.put("description", s);
                    hotTopics.add(hm);
                }
            }
            plan.put("hotTopics", hotTopics);

            // Quizzes
            java.util.List quizzes = (java.util.List) plan.get("quizzes");
            if (quizzes == null) quizzes = new java.util.ArrayList();
            // sensible defaults
            if (quizLimit <= 0) quizLimit = 3;
            if (questionsLimit <= 0) questionsLimit = 5;
            // Try to generate quizzes if requested OR if none present
            if ((includeQuizzes || quizzes.isEmpty()) && quizzes.size() < quizLimit) {
                // Ask OpenAI specifically for quizzes if possible
                if (client != null && apiKey != null && !apiKey.isEmpty()) {
                    try {
                        String instr = "You MUST respond with ONLY a JSON array (no explanation, no markdown).\n" +
                                "Return up to " + quizLimit + " quizzes extracted from the DOCUMENT.\n" +
                                "Each quiz element must be an object with keys: \"id\" (string), \"titulo\" (string), \"progreso\" (object), and \"questions\" (array).\n" +
                                "Each question must be an object with keys: \"question\" (string), \"options\" (array of strings), and \"answer\" (one of the options).\n" +
                                "Example response (one quiz with one question):\n" +
                                "[ {\"id\": \"qz1\", \"titulo\": \"Repaso 1\", \"progreso\": {\"currentIndex\":0}, \"questions\": [ {\"question\":\"¿Qué es X?\", \"options\": [\"A\",\"B\"], \"answer\":\"A\"} ] } ]\n\n" +
                                "DOCUMENT:\n" + extractedText + "\n\nReturn up to " + questionsLimit + " questions per quiz.";
                        String resp = RetryUtils.runWithRetries(() -> client.generateJsonForSection(apiKey, instr, 800), 2, 500L, (Exception ex) -> {
                            if (ex instanceof com.app.estudiaapp.utils.OpenAiException) return ((com.app.estudiaapp.utils.OpenAiException) ex).isRetryable();
                            return true;
                        });
                        String json = extractFirstJson(resp);
                        if (json != null) {
                            try {
                                com.google.gson.Gson gg = new com.google.gson.Gson();
                                java.util.List parsedQz = gg.fromJson(json, java.util.List.class);
                                if (parsedQz != null && parsedQz.size() > 0) {
                                    quizzes.clear();
                                    for (Object o : parsedQz) quizzes.add(o);
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "OpenAI quizzes request failed: " + e.getMessage());
                    }
                }
                int needQz = Math.max(0, quizLimit - quizzes.size());
                java.util.List<String> allSnips = extractTopSnippets(extractedText, Math.max(questionsLimit * needQz, 10));
                for (int qi = 0; qi < needQz; qi++) {
                    java.util.Map<String,Object> qmap = new java.util.HashMap<>();
                    String qid = String.format("qz%d", quizzes.size());
                    qmap.put("id", qid);
                    qmap.put("titulo", String.format("Quiz %d", quizzes.size() + 1));
                    java.util.Map<String,Object> progreso = new java.util.HashMap<>();
                    progreso.put("currentIndex", 0);
                    progreso.put("lastUpdatedMillis", 0L);
                    progreso.put("nota", -1);
                    progreso.put("preguntasCorrectas", 0);
                    progreso.put("preguntasIncorrectas", 0);
                    progreso.put("preguntasRespondidas", 0);
                    qmap.put("progreso", progreso);
                    java.util.List<java.util.Map<String,Object>> questions = new java.util.ArrayList<>();
                    int qCount = Math.max(1, questionsLimit);
                    for (int qi2 = 0; qi2 < qCount; qi2++) {
                        java.util.Map<String,Object> qm = new java.util.HashMap<>();
                        int snipIndex = qi * qCount + qi2;
                        String snippet = snipIndex < allSnips.size() ? allSnips.get(snipIndex) : "(Contenido del documento)";
                        String shortSnippet = snippet.length() > 100 ? snippet.substring(0, 100) + "..." : snippet;
                        String question = String.format("Según el documento, ¿qué afirma sobre: '%s'?", shortSnippet);

                        // Build options: correct = concise version of snippet, distractors from other snippets
                        java.util.List<String> options = new java.util.ArrayList<>();
                        String correct = shortSnippet;
                        options.add(correct);
                        // add up to 3 distractors from other snippets
                        int added = 0;
                        for (int d = 0; d < allSnips.size() && added < 3; d++) {
                            if (d == snipIndex) continue;
                            String cand = allSnips.get(d);
                            if (cand == null || cand.trim().isEmpty()) continue;
                            String candShort = cand.length() > 80 ? cand.substring(0, 80) + "..." : cand;
                            if (!options.contains(candShort)) { options.add(candShort); added++; }
                        }
                        while (options.size() < 4) options.add("Ninguna de las anteriores");

                        qm.put("question", question);
                        qm.put("options", options);
                        qm.put("answer", correct);
                        questions.add(qm);
                    }
                    qmap.put("questions", questions);
                    quizzes.add(qmap);
                }
            }
            plan.put("quizzes", quizzes);

            // Ensure progreso exists
            java.util.Map progreso = (java.util.Map) plan.get("progreso");
            if (progreso == null) progreso = new java.util.HashMap();
            progreso.putIfAbsent("estimatedStudyTimeMinutes", 0);
            progreso.putIfAbsent("flashcardsEstudiadas", 0);
            progreso.putIfAbsent("nivel", 1);
            progreso.putIfAbsent("preguntasCorrectas", 0);
            progreso.putIfAbsent("preguntasRespondidas", 0);
            plan.put("progreso", progreso);

        } catch (Exception e) {
            Log.w(TAG, "validateAndFillPlan failed: " + e.getMessage());
        }
    }

    // Simple helper to extract top-sentences/snippets from text
    private java.util.List<String> extractTopSnippets(String text, int count) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        try {
            // split on periods, newlines and take trimmed sentences
            String[] parts = text.split("\\\n|\\.\\s+|\\.\r|\\.\n");
            for (String p : parts) {
                String s = p.trim();
                if (s.length() > 20) out.add(s);
                if (out.size() >= count) break;
            }
            // If not enough, add smaller fragments
            if (out.size() < count) {
                String[] tokens = text.split("\\s+");
                for (int i = 0; out.size() < count && i < tokens.length; i += 10) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i; j < Math.min(tokens.length, i+10); j++) { sb.append(tokens[j]).append(' '); }
                    out.add(sb.toString().trim());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    // Attempt to extract the first balanced JSON array or object from the provided text.
    // Returns the JSON substring (trimmed) or null if none found.
    private String extractFirstJson(String text) {
        if (text == null) return null;
        String s = text.trim();
        // quick checks
        int firstBrace = s.indexOf('{');
        int firstBracket = s.indexOf('[');
        int start = -1;
        char open = 0;
        char close = 0;
        if (firstBracket >= 0 && (firstBracket < firstBrace || firstBrace < 0)) {
            start = firstBracket; open = '['; close = ']';
        } else if (firstBrace >= 0) {
            start = firstBrace; open = '{'; close = '}';
        }
        if (start < 0) return null;

        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) {
                    String candidate = s.substring(start, i + 1).trim();
                    // quick sanity: must start with open
                    if ((open == '[' && candidate.startsWith("[")) || (open == '{' && candidate.startsWith("{"))) return candidate;
                }
            }
        }
        return null;
    }
}
