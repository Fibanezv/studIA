package com.app.estudiaapp.helper;

import android.content.Context;

import com.app.estudiaapp.models.Flashcard;
import com.app.estudiaapp.models.Question;
import com.app.estudiaapp.models.HotTopic;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.UserProgress;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.QuizProgress;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Type;

public class StorageHelper {

    public static void guardarPlan(Context context, StudyPlan plan) {
        Gson gson = new Gson();
        String json = gson.toJson(plan);
        try (FileOutputStream fos = context.openFileOutput(plan.id + ".json", Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static StudyPlan cargarPlan(Context context, String planId) {
        Gson gson = new Gson();
        // Read whole file using try-with-resources to ensure streams are closed
        try (FileInputStream fis = context.openFileInput(planId + ".json");
             InputStreamReader reader = new InputStreamReader(fis)) {

            // Leer el JSON crudo para detectar formatos antiguos
            JsonObject obj = gson.fromJson(reader, JsonObject.class);
            if (obj != null && obj.isJsonObject()) {

                // Formato antiguo: campo "cuestionarios" (lista plana de preguntas)
                if (obj.has("cuestionarios") && !obj.has("quizzes")) {
                    // Convertir: dividir las preguntas antiguas en bloques de N preguntas por quiz
                    final int CHUNK = 10; // puedes ajustar este valor
                    StudyPlan plan = new StudyPlan(planId, obj.has("nombre") ? obj.get("nombre").getAsString() : "Plan migrado");
                    Type listType = new TypeToken<List<Question>>(){}.getType();
                    List<Question> preguntas = gson.fromJson(obj.get("cuestionarios"), listType);

                    if (preguntas != null && !preguntas.isEmpty()) {
                        int idx = 0;
                        for (int i = 0; i < preguntas.size(); i += CHUNK) {
                            int end = Math.min(i + CHUNK, preguntas.size());
                            List<Question> sub = preguntas.subList(i, end);
                            String qid = "qz" + idx;
                            Quiz qz = new Quiz(qid, "Quiz " + (idx + 1));
                            qz.questions.addAll(sub);
                            qz.progreso = new QuizProgress();
                            plan.quizzes.add(qz);
                            idx++;
                        }
                    }

                    // flashcards y progreso si existen
                    if (obj.has("flashcards")) {
                        Type ftype = new TypeToken<List<Flashcard>>(){}.getType();
                        List<Flashcard> f = gson.fromJson(obj.get("flashcards"), ftype);
                        if (f != null) plan.flashcards.addAll(f);
                    }
                    plan.progreso = new UserProgress();
                    // Reescribir el plan migrado para futuras cargas
                    guardarPlan(context, plan);
                    return plan;
                }

                // Si ya tiene "quizzes" usamos la deserialización directa
                if (obj.has("quizzes")) {
                    // Volver a crear desde el string JSON
                    String json = obj.toString();
                        StudyPlan loaded = gson.fromJson(json, StudyPlan.class);
                        // Sanitize loaded plan: remove obvious placeholder/generated items
                        sanitizePlan(loaded);
                        // Persist sanitized plan back to local storage so placeholders do not reappear
                        try { guardarPlan(context, loaded); } catch (Exception ignored) {}
                        return loaded;
                }
            }

            // Fallback: intentar deserializar directamente leyendo el contenido completo
            try (FileInputStream fis2 = context.openFileInput(planId + ".json");
                 InputStreamReader reader2 = new InputStreamReader(fis2)) {
                StudyPlan loaded = gson.fromJson(reader2, StudyPlan.class);
                sanitizePlan(loaded);
                try { guardarPlan(context, loaded); } catch (Exception ignored) {}
                return loaded;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Remove common placeholder/generated items created by older flows so UI shows empty lists.
    private static void sanitizePlan(StudyPlan plan) {
        if (plan == null) return;
        // Flashcards: remove entries that look like placeholders
        try {
            if (plan.flashcards != null) {
                java.util.Iterator<Flashcard> it = plan.flashcards.iterator();
                while (it.hasNext()) {
                    Flashcard f = it.next();
                    if (f == null) { it.remove(); continue; }
                    String q = f.pregunta == null ? "" : f.pregunta.toLowerCase();
                    String a = f.respuesta == null ? "" : f.respuesta.toLowerCase();
                    boolean placeholder = false;
                    if (q.contains("(sin pregunta)") || a.contains("(sin respuesta)")) placeholder = true;
                    if (q.matches("^ficha\\s*\\d+.*")) placeholder = true;
                    if (q.contains("pregunta de ejemplo") || q.contains("ejemplo") || q.contains("generado")) placeholder = true;
                    if (a.contains("generado") || a.contains("ejemplo")) placeholder = true;
                    if (placeholder) it.remove();
                }
            }
        } catch (Exception ignored) {}

        // HotTopics: remove entries with clearly empty or placeholder titles
        try {
            if (plan.hotTopics != null) {
                java.util.Iterator<HotTopic> it = plan.hotTopics.iterator();
                while (it.hasNext()) {
                    HotTopic t = it.next();
                    if (t == null) { it.remove(); continue; }
                    String title = t.title == null ? "" : t.title.toLowerCase();
                    if (title.contains("(sin titulo)") || title.trim().isEmpty()) {
                        it.remove();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Quizzes: remove questions that look placeholder, and drop quizzes with no questions
        try {
            if (plan.quizzes != null) {
                java.util.Iterator<Quiz> itq = plan.quizzes.iterator();
                while (itq.hasNext()) {
                    Quiz qz = itq.next();
                    if (qz == null) { itq.remove(); continue; }
                    if (qz.questions != null) {
                        java.util.Iterator<Question> itq2 = qz.questions.iterator();
                        while (itq2.hasNext()) {
                            Question qq = itq2.next();
                            if (qq == null) { itq2.remove(); continue; }
                            String qtxt = qq.question == null ? "" : qq.question.toLowerCase();
                            if (qtxt.contains("pregunta de ejemplo") || qtxt.contains("generado") || qtxt.matches("^pregunta\\s*\\d+.*")) {
                                itq2.remove();
                            }
                        }
                    }
                    if (qz.questions == null || qz.questions.isEmpty()) {
                        itq.remove();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static ArrayList<StudyPlan> listarPlanes(Context context) {
        ArrayList<StudyPlan> planes = new ArrayList<>();
        String[] files = context.fileList();
        for (String file : files) {
            if (file.endsWith(".json")) {
                String id = file.replace(".json", "");
                StudyPlan plan = cargarPlan(context, id);
                if (plan != null) {
                    planes.add(plan);
                }
            }
        }
        return planes;
    }

    public static void crearPlanSimuladoSiNoExiste(Context context) {
        // Sample-plan creation disabled.
        // Historically this method created example plans on first run which interfered
        // with newly generated plans coming from the OpenAI/Firestore flow. To avoid
        // writing example content into local storage and accidentally migrating it
        // to Firestore, this helper is now intentionally a no-op.
        return;
    }

}
