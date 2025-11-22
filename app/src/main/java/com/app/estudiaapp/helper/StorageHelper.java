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
                    return gson.fromJson(json, StudyPlan.class);
                }
            }

            // Fallback: intentar deserializar directamente leyendo el contenido completo
            try (FileInputStream fis2 = context.openFileInput(planId + ".json");
                 InputStreamReader reader2 = new InputStreamReader(fis2)) {
                return gson.fromJson(reader2, StudyPlan.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
