package com.app.estudiaapp.workers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// Document extraction has been removed from the app. The worker now expects
// pre-extracted text provided via the input data key `INPUT_EXTRACTED_TEXT`.
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.Timestamp;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
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
    // Legacy: level/days/goal removed. New inputs:
    public static final String INPUT_LIMITS_JSON = "input_limits_json"; // JSON string with limits
    public static final String INPUT_MEMBERSHIP = "input_membership"; // free|pro|premium
    public static final String INPUT_LANGUAGE = "input_language";
    public static final String INPUT_QUIZ_TYPOLOGY = "input_quiz_typology";
    public static final String INPUT_PRIORITY_POINTS = "input_priority_points";
    public static final String INPUT_LEVEL = "input_level";

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
            // New flow: upload the PDF to Firebase Storage, then call a Cloud Function
            // `generateStudyPlan` which will call OpenAI, save the plan in Firestore and
            // return the plan id + JSON. The worker simply orchestrates upload + function call.

            setProgressAsync(new Data.Builder().putString("status", "uploading").build());

            FirebaseStorage storage = FirebaseStorage.getInstance();
            String uid = "unknown";
            try { uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

            String uploadId = UUID.randomUUID().toString();
            com.google.firebase.storage.StorageReference ref = storage.getReference().child("users/" + uid + "/uploads/" + uploadId + "/source.pdf");

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

            // Obtain a public download URL for the uploaded file
            final AtomicReference<String> downloadUrlRef = new AtomicReference<>();
            final CountDownLatch urlLatch = new CountDownLatch(1);
            try {
                ref.getDownloadUrl().addOnSuccessListener(uri1 -> {
                    downloadUrlRef.set(uri1.toString());
                    urlLatch.countDown();
                }).addOnFailureListener(e -> {
                    uploadEx.set(e);
                    urlLatch.countDown();
                });
            } catch (Exception e) {
                return failureWithMessage("Failed to request download URL: " + e.getMessage());
            }
            while (urlLatch.getCount() > 0) {
                if (isStopped()) return failureWithMessage("Cancelled");
                urlLatch.await(200, TimeUnit.MILLISECONDS);
            }
            if (uploadEx.get() != null) {
                return failureWithMessage("Failed to obtain download URL: " + uploadEx.get().getMessage());
            }

            String pdfUrl = downloadUrlRef.get();
            if (pdfUrl == null || pdfUrl.isEmpty()) return failureWithMessage("Could not obtain file URL");

            // Prepare data for cloud function
            // New flow: pass limits as a JSON string and membership. Older nivel/dias/objetivo removed.
            String limitsJson = getInputData().getString(INPUT_LIMITS_JSON);
            String membership = getInputData().getString(INPUT_MEMBERSHIP);
            String inputTitle = getInputData().getString(INPUT_TITLE);
            if (membership == null || membership.trim().isEmpty()) membership = "free";
            java.util.Map limitsMap = null;
            if (limitsJson != null) {
                try {
                    limitsMap = new Gson().fromJson(limitsJson, java.util.Map.class);
                } catch (Exception ignored) { limitsMap = null; }
            }

            setProgressAsync(new Data.Builder().putString("status", "calling_function").build());

            final AtomicReference<String> planIdRef = new AtomicReference<>();
            final AtomicReference<java.util.Map> planMapRef = new AtomicReference<>();
            final AtomicReference<Exception> funcEx = new AtomicReference<>();
            final CountDownLatch funcLatch = new CountDownLatch(1);

            // Collect form configuration options to forward to the Cloud Function
            boolean includeQuizzes = getInputData().getBoolean(INPUT_INCLUDE_QUIZZES, false);
            int quizLimit = getInputData().getInt(INPUT_QUIZ_LIMIT, 0);
            int questionsLimit = getInputData().getInt(INPUT_QUESTIONS_LIMIT, 0);
            boolean includeFlashcards = getInputData().getBoolean(INPUT_INCLUDE_FLASHCARDS, false);
            int flashcardLimit = getInputData().getInt(INPUT_FLASHCARD_LIMIT, 0);
            boolean includeHotTopics = getInputData().getBoolean(INPUT_INCLUDE_HOT_TOPICS, false);

                // Allocate canonical plan id on-device so saved document IDs are predictable (U0#####)
                String clientPlanId = null;
                try {
                    clientPlanId = allocateCanonicalPlanId(FirebaseFirestore.getInstance(), uid);
                } catch (InterruptedException e) {
                    // ignore and let server create its own uuid
                    clientPlanId = null;
                }

                java.util.Map<String, Object> funcPayload = new java.util.HashMap<>();
                funcPayload.put("pdfUrl", pdfUrl);
                funcPayload.put("membership", membership);
                funcPayload.put("limits", limitsMap != null ? limitsMap : new java.util.HashMap<String,Object>());
                    // include language/typology/priority points if provided
                    try {
                        String lang = getInputData().getString(INPUT_LANGUAGE);
                        if (lang != null) funcPayload.put("language", lang);
                    } catch (Exception ignored) {}
                    try {
                        String typ = getInputData().getString(INPUT_QUIZ_TYPOLOGY);
                        if (typ != null) funcPayload.put("quizTypology", typ);
                    } catch (Exception ignored) {}
                    try {
                        String pp = getInputData().getString(INPUT_PRIORITY_POINTS);
                        if (pp != null) funcPayload.put("priorityPoints", pp);
                    } catch (Exception ignored) {}
                    try {
                        String lvl = getInputData().getString(INPUT_LEVEL);
                        if (lvl != null) funcPayload.put("difficulty", lvl);
                    } catch (Exception ignored) {}
                if (clientPlanId != null) funcPayload.put("clientPlanId", clientPlanId);
                if (inputTitle != null && !inputTitle.trim().isEmpty()) funcPayload.put("inputTitle", inputTitle.trim());
                funcPayload.put("includeQuizzes", includeQuizzes);
                funcPayload.put("quizLimit", quizLimit);
                funcPayload.put("questionsLimit", questionsLimit);
                funcPayload.put("includeFlashcards", includeFlashcards);
                funcPayload.put("flashcardLimit", flashcardLimit);
                funcPayload.put("includeHotTopics", includeHotTopics);

                // Call the Cloud Function with reduced timeout since it returns immediately
                // NEW: Function returns planId in ~2-5 seconds, content generates in background
                final int MAX_ATTEMPTS = 2;
                final long WAIT_PER_ATTEMPT_MS = 30000; // 30s = sufficient for immediate response
                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    final CountDownLatch attemptLatch = new CountDownLatch(1);
                    // reset exception for this attempt
                    funcEx.set(null);
                    planIdRef.set(null);
                    planMapRef.set(null);

                    FirebaseFunctions.getInstance()
                        .getHttpsCallable("generateStudyPlan")
                        .call(funcPayload)
                        .addOnSuccessListener((HttpsCallableResult result) -> {
                            try {
                                Object data = result.getData();
                                if (data instanceof java.util.Map) {
                                    java.util.Map map = (java.util.Map) data;
                                    Object id = map.get("id");
                                    Object plan = map.get("plan");
                                    if (id != null) planIdRef.set(String.valueOf(id));
                                    if (plan instanceof java.util.Map) planMapRef.set((java.util.Map) plan);
                                }
                            } catch (Exception e) {
                                funcEx.set(e);
                            } finally {
                                attemptLatch.countDown();
                            }
                        }).addOnFailureListener(e -> {
                            funcEx.set(e instanceof Exception ? (Exception) e : new Exception(e.toString()));
                            attemptLatch.countDown();
                        });

                    long waited = 0L;
                    while (attemptLatch.getCount() > 0 && waited < WAIT_PER_ATTEMPT_MS) {
                        if (isStopped()) return failureWithMessage("Cancelled");
                        long toWait = Math.min(5000, WAIT_PER_ATTEMPT_MS - waited);
                        attemptLatch.await(toWait, TimeUnit.MILLISECONDS);
                        waited += toWait;
                        // Update progress every 10 seconds
                        if (waited % 10000 < 5000) {
                            int secondsWaited = (int)(waited / 1000);
                            setProgressAsync(new Data.Builder()
                                .putString("status", "Generando plan... (" + secondsWaited + "s)")
                                .build());
                        }
                    }

                    if (attemptLatch.getCount() > 0) {
                        // this attempt timed out waiting for the callable listener
                        if (attempt < MAX_ATTEMPTS) {
                            Log.w(TAG, "Attempt " + attempt + " timed out waiting for Cloud Function; retrying...");
                            try { Thread.sleep(1000 * attempt); } catch (InterruptedException ignored) {}
                            continue; // retry
                        } else {
                            funcEx.set(new Exception("Callable request timed out after " + WAIT_PER_ATTEMPT_MS + "ms"));
                        }
                    }

                    // If there was an exception captured by listeners, break and handle it
                    if (funcEx.get() != null) break;
                    // If we got a result, break out of retry loop
                    if (planIdRef.get() != null) break;
                }

            if (funcEx.get() != null) {
                Log.w(TAG, "Cloud Function failed: " + funcEx.get().getMessage());
                return failureWithMessage("Cloud Function failed: " + (funcEx.get().getMessage() != null ? funcEx.get().getMessage() : "unknown"));
            }

            String returnedPlanId = planIdRef.get();
            if (returnedPlanId == null || returnedPlanId.isEmpty()) {
                return failureWithMessage("Cloud Function did not return a plan id");
            }

            setProgressAsync(new Data.Builder().putString("status", "done").build());
            return Result.success(new Data.Builder().putString("planId", returnedPlanId).build());

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
                // Only include flashcards that have both question and answer
                if (pregunta != null && respuesta != null) {
                    fm.put("pregunta", pregunta);
                    fm.put("respuesta", respuesta);
                    fm.put("completada", completada);
                    flashcards.add(fm);
                } else {
                    // skip incomplete/placeholder flashcards
                }
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
                    // Only include hotTopics with a title (and optional description)
                    if (title != null && !title.trim().isEmpty()) {
                        if (desc == null) desc = "";
                        hm.put("title", title);
                        hm.put("description", desc);
                        hotTopics.add(hm);
                    }
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
                                // Only include questions that have a question text and at least an answer or options
                                if (question != null && (!options.isEmpty() || (answer != null && !answer.trim().isEmpty()))) {
                                    qmap.put("question", question);
                                    qmap.put("options", options);
                                    qmap.put("answer", answer != null ? answer : (options.size() > 0 ? options.get(0) : ""));
                                    questions.add(qmap);
                                } else {
                                    // skip this question
                                }
                            }
                        }
                    }
                }
                // Only include quizzes that have at least one valid question
                if (questions.size() > 0) {
                    if (qid == null) qid = String.format("qz%d", qi);
                    if (titulo == null) titulo = "Quiz " + (qi + 1);
                    qout.put("id", qid);
                    qout.put("titulo", titulo);
                    qout.put("progreso", progreso);
                    qout.put("questions", questions);
                    quizzes.add(qout);
                } else {
                    // skip quiz without valid questions
                }
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
                                     boolean includeHotTopics) {
        if (plan == null) return;
        try {
            // Title override
            if (inputTitle != null && !inputTitle.trim().isEmpty()) plan.put("nombre", inputTitle.trim());

            // Flashcards: ensure list exists but do not auto-generate placeholders
            java.util.List flashcards = (java.util.List) plan.get("flashcards");
            if (flashcards == null) flashcards = new java.util.ArrayList();
            plan.put("flashcards", flashcards);

            // Hot topics: ensure list exists but do not auto-generate placeholders
            java.util.List hotTopics = (java.util.List) plan.get("hotTopics");
            if (hotTopics == null) hotTopics = new java.util.ArrayList();
            plan.put("hotTopics", hotTopics);

            // Quizzes: ensure list exists but do not auto-generate placeholders
            java.util.List quizzes = (java.util.List) plan.get("quizzes");
            if (quizzes == null) quizzes = new java.util.ArrayList();
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
