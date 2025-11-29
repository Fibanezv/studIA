package com.app.estudiaapp.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SchemaUtils {

    /**
     * Returns a JSON Schema (as Gson JsonObject) matching the StudyPlan structure
     * that we store under users/{uid}/plans/{planId}.
     */
    public static JsonObject getStudyPlanSchema() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");

        JsonObject props = new JsonObject();

        props.add("id", strSchema());
        props.add("nombre", strSchema());
        props.add("sourcePath", strSchema());
        props.add("sourceUrl", strSchema());
        props.add("estimatedStudyTimeMinutes", intSchema());
        props.add("flashcardsEstudiadas", intSchema());
        props.add("nivel", intSchema());

        // progreso (object)
        JsonObject progreso = new JsonObject();
        progreso.addProperty("type", "object");
        JsonObject progresoProps = new JsonObject();
        progresoProps.add("estimatedStudyTimeMinutes", intSchema());
        progresoProps.add("flashcardsEstudiadas", intSchema());
        progresoProps.add("nivel", intSchema());
        progresoProps.add("preguntasRespondidas", intSchema());
        progresoProps.add("preguntasCorrectas", intSchema());
        progreso.add("properties", progresoProps);
        progreso.add("required", emptyArray());
        progreso.addProperty("additionalProperties", false);
        props.add("progreso", progreso);

        // flashcards (array of objects)
        JsonObject flashcards = new JsonObject();
        flashcards.addProperty("type", "array");
        JsonObject fcItem = new JsonObject(); fcItem.addProperty("type", "object");
        JsonObject fcProps = new JsonObject();
        fcProps.add("pregunta", strSchema());
        fcProps.add("respuesta", strSchema());
        fcProps.add("completada", boolSchema());
        fcItem.add("properties", fcProps);
        fcItem.add("required", emptyArray());
        fcItem.addProperty("additionalProperties", false);
        flashcards.add("items", fcItem);
        props.add("flashcards", flashcards);

        // hotTopics
        JsonObject hot = new JsonObject(); hot.addProperty("type", "array");
        JsonObject htItem = new JsonObject(); htItem.addProperty("type", "object");
        JsonObject htProps = new JsonObject(); htProps.add("title", strSchema()); htProps.add("description", strSchema());
        htItem.add("properties", htProps); htItem.add("required", emptyArray()); htItem.addProperty("additionalProperties", false);
        hot.add("items", htItem);
        props.add("hotTopics", hot);

        // quizzes (array)
        JsonObject quizzes = new JsonObject(); quizzes.addProperty("type", "array");
        JsonObject qItem = new JsonObject(); qItem.addProperty("type", "object");
        JsonObject qProps = new JsonObject();
        qProps.add("id", strSchema()); qProps.add("titulo", strSchema());
        // progreso inside quiz
        JsonObject qProg = new JsonObject(); qProg.addProperty("type", "object"); qProg.add("properties", emptyObject()); qProg.add("required", emptyArray()); qProg.addProperty("additionalProperties", false);
        qProps.add("progreso", qProg);
        // questions array
        JsonObject questions = new JsonObject(); questions.addProperty("type", "array");
        JsonObject questionItem = new JsonObject(); questionItem.addProperty("type", "object");
        JsonObject questionProps = new JsonObject(); questionProps.add("question", strSchema()); questionProps.add("answer", strSchema()); questionProps.add("options", arrayOfStrings());
        questionItem.add("properties", questionProps); questionItem.add("required", emptyArray()); questionItem.addProperty("additionalProperties", false);
        questions.add("items", questionItem);
        qProps.add("questions", questions);

        qItem.add("properties", qProps); qItem.add("required", emptyArray()); qItem.addProperty("additionalProperties", false);
        quizzes.add("items", qItem);
        props.add("quizzes", quizzes);

        root.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("id"); required.add("nombre"); required.add("flashcards"); required.add("hotTopics"); required.add("progreso"); required.add("quizzes");
        root.add("required", required);
        root.addProperty("additionalProperties", false);

        return root;
    }

    private static JsonObject strSchema() { JsonObject o = new JsonObject(); o.addProperty("type", "string"); return o; }
    private static JsonObject intSchema() { JsonObject o = new JsonObject(); o.addProperty("type", "integer"); return o; }
    private static JsonObject boolSchema() { JsonObject o = new JsonObject(); o.addProperty("type", "boolean"); return o; }
    private static JsonArray emptyArray() { return new JsonArray(); }
    private static JsonObject emptyObject() { return new JsonObject(); }
    private static JsonObject arrayOfStrings() { JsonObject a = new JsonObject(); a.addProperty("type", "array"); JsonObject it = new JsonObject(); it.addProperty("type", "string"); a.add("items", it); return a; }
}
