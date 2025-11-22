package com.app.estudiaapp.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import android.util.Log;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OpenAiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final Gson gson = new Gson();

    public OpenAiClient() {
        this.client = new OkHttpClient.Builder().build();
    }

    private void logRawResponse(String prefix, String respBody) {
        try {
            if (!isDebug()) return;
            // Limit size to avoid log flooding
            int max = 16 * 1024; // 16 KB
            String out = respBody == null ? "" : (respBody.length() > max ? respBody.substring(0, max) + "...(truncated)" : respBody);
            Log.d("OpenAiClient", prefix + " " + out);
        } catch (Exception ignored) {}
    }

    private boolean isDebug() {
        try {
            Class<?> bc = Class.forName("com.app.estudiaapp.BuildConfig");
            try {
                java.lang.reflect.Field f = bc.getField("DEBUG");
                return f.getBoolean(null);
            } catch (Exception e) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    // Summarize a chunk of text into a short plain-text summary.
    public String summarizeChunk(String apiKey, String chunkText) throws IOException, OpenAiException {
        if (apiKey == null || apiKey.isEmpty()) throw new IOException("OpenAI API key is empty");

        String systemPrompt = "You are a concise summarizer. Return only the summary text (no JSON or extra commentary).";
        String userPrompt = "Summarize the following text in a concise paragraph (up to ~300 words):\n" + (chunkText == null ? "" : chunkText);

        Map<String, Object> message0 = new HashMap<>();
        message0.put("role", "system");
        message0.put("content", systemPrompt);
        Map<String, Object> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("messages", new Object[]{message0, message1});
        body.put("temperature", 0.0);
        body.put("max_tokens", 700);

        String jsonBody = gson.toJson(body);

        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp == null) throw new IOException("No response from OpenAI");
            String respBody = resp.body() != null ? resp.body().string() : "";
            logRawResponse("summarizeChunk response:", respBody);
            if (!resp.isSuccessful()) {
                throw new OpenAiException(resp.code(), respBody);
            }

            JsonObject root = gson.fromJson(respBody, JsonObject.class);
            if (root == null) return respBody;
            try {
                JsonElement choices = root.get("choices");
                if (choices != null && choices.isJsonArray() && choices.getAsJsonArray().size() > 0) {
                    JsonObject first = choices.getAsJsonArray().get(0).getAsJsonObject();
                    JsonElement message = first.get("message");
                    if (message != null && message.isJsonObject()) {
                        JsonElement content = message.getAsJsonObject().get("content");
                        if (content != null) return content.getAsString();
                    }
                }
            } catch (Exception ignored) {}

            return respBody;
        }
    }

    // Generate a StudyPlan JSON (raw model output as string)
    public String generateStudyPlan(String apiKey, String extractedText) throws IOException, OpenAiException {
        if (apiKey == null || apiKey.isEmpty()) throw new IOException("OpenAI API key is empty");

        String systemPrompt = "You are a generator that MUST return ONLY a single JSON object (no surrounding text).\n" +
                "Produce a StudyPlan JSON for the provided document. The JSON must be parseable.\n" +
                "If you cannot produce a JSON, return a valid empty JSON object {}.";

        String userPrompt = "Document text:\n" + (extractedText == null ? "" : extractedText) + "\n\n" +
                "Return a compact JSON object representing a study plan (topics, schedule, duration, resources).";

        Map<String, Object> message0 = new HashMap<>();
        message0.put("role", "system");
        message0.put("content", systemPrompt);
        Map<String, Object> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("messages", new Object[]{message0, message1});
        body.put("temperature", 0.0);
        body.put("max_tokens", 2000);

        String jsonBody = gson.toJson(body);

        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp == null) throw new IOException("No response from OpenAI");
            String respBody = resp.body() != null ? resp.body().string() : "";
            logRawResponse("generateStudyPlan response:", respBody);
            if (!resp.isSuccessful()) {
                throw new OpenAiException(resp.code(), respBody);
            }

            JsonObject root = gson.fromJson(respBody, JsonObject.class);
            if (root == null) return respBody;
            try {
                JsonElement choices = root.get("choices");
                if (choices != null && choices.isJsonArray() && choices.getAsJsonArray().size() > 0) {
                    JsonObject first = choices.getAsJsonArray().get(0).getAsJsonObject();
                    JsonElement message = first.get("message");
                    if (message != null && message.isJsonObject()) {
                        JsonElement content = message.getAsJsonObject().get("content");
                        if (content != null) return content.getAsString();
                    }
                }
            } catch (Exception ignored) {}

            return respBody;
        }
    }

    // Request a specific JSON section (strict JSON output)
    public String generateJsonForSection(String apiKey, String instruction, int maxTokens) throws IOException, OpenAiException {
        if (apiKey == null || apiKey.isEmpty()) throw new IOException("OpenAI API key is empty");

        String systemPrompt = "You are a strict JSON generator. Respond with ONLY the JSON requested by the user and nothing else. Do not include any explanatory text or markdown.";
        String userPrompt = instruction;

        Map<String, Object> message0 = new HashMap<>();
        message0.put("role", "system");
        message0.put("content", systemPrompt);
        Map<String, Object> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("messages", new Object[]{message0, message1});
        body.put("temperature", 0.0);
        if (maxTokens > 0) body.put("max_tokens", maxTokens);

        String jsonBody = gson.toJson(body);

        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp == null) throw new IOException("No response from OpenAI");
            String respBody = resp.body() != null ? resp.body().string() : "";
            logRawResponse("generateJsonForSection response:", respBody);
            if (!resp.isSuccessful()) {
                throw new OpenAiException(resp.code(), respBody);
            }

            JsonObject root = gson.fromJson(respBody, JsonObject.class);
            if (root == null) return respBody;
            try {
                JsonElement choices = root.get("choices");
                if (choices != null && choices.isJsonArray() && choices.getAsJsonArray().size() > 0) {
                    JsonObject first = choices.getAsJsonArray().get(0).getAsJsonObject();
                    JsonElement message = first.get("message");
                    if (message != null && message.isJsonObject()) {
                        JsonElement content = message.getAsJsonObject().get("content");
                        if (content != null) return content.getAsString();
                    }
                }
            } catch (Exception ignored) {}

            return respBody;
        }
    }

    // Generate distractors as a JSON array of strings
    public String generateDistractors(String apiKey, String contextSnippet, String correctAnswer, int n, int maxTokens) throws IOException, OpenAiException {
        if (apiKey == null || apiKey.isEmpty()) throw new IOException("OpenAI API key is empty");

        String systemPrompt = "You are a generator that must ONLY return a JSON array of short plausible distractor strings (no explanation, no markdown).";
        String userPrompt = "Given the following CONTEXT and the CORRECT_ANSWER, return up to " + n + " plausible distractors (brief phrases) as a JSON array. Do not repeat the correct answer.\n\nCONTEXT:\n" + (contextSnippet == null ? "" : contextSnippet) + "\n\nCORRECT_ANSWER:\n" + (correctAnswer == null ? "" : correctAnswer) + "\n\nExample response:\n[\"Distractor A\", \"Distractor B\"]";

        Map<String, Object> message0 = new HashMap<>();
        message0.put("role", "system");
        message0.put("content", systemPrompt);
        Map<String, Object> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", userPrompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("messages", new Object[]{message0, message1});
        body.put("temperature", 0.0);
        if (maxTokens > 0) body.put("max_tokens", maxTokens);

        String jsonBody = gson.toJson(body);

        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp == null) throw new IOException("No response from OpenAI");
            String respBody = resp.body() != null ? resp.body().string() : "";
            logRawResponse("generateDistractors response:", respBody);
            if (!resp.isSuccessful()) {
                throw new OpenAiException(resp.code(), respBody);
            }

            JsonObject root = gson.fromJson(respBody, JsonObject.class);
            if (root == null) return respBody;
            try {
                JsonElement choices = root.get("choices");
                if (choices != null && choices.isJsonArray() && choices.getAsJsonArray().size() > 0) {
                    JsonObject first = choices.getAsJsonArray().get(0).getAsJsonObject();
                    JsonElement message = first.get("message");
                    if (message != null && message.isJsonObject()) {
                        JsonElement content = message.getAsJsonObject().get("content");
                        if (content != null) return content.getAsString();
                    }
                }
            } catch (Exception ignored) {}

            return respBody;
        }
    }

    /**
     * Generate a StudyPlan using the Responses endpoint and a provided JSON Schema.
     * Returns the raw response body (caller should parse the structured output).
     */
    public String generateStudyPlanWithSchema(String apiKey, String title, String language, String level,
                                              int quizLimit, int questionsLimit, int flashcardLimit, int hotTopicsLimit,
                                              String extractedText, JsonObject jsonSchema) throws IOException, OpenAiException {
        if (apiKey == null || apiKey.isEmpty()) throw new IOException("OpenAI API key is empty");

        // Build response_format
        JsonObject rf = new JsonObject();
        rf.addProperty("type", "json_schema");
        JsonObject js = new JsonObject();
        js.addProperty("name", "StudyPlan");
        js.add("schema", jsonSchema == null ? new JsonObject() : jsonSchema);
        js.addProperty("strict", true);
        rf.add("json_schema", js);

        // Build input messages array following Responses API shape
        JsonArray input = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", "Eres una IA experta en pedagogía que crea planes de estudio en formato JSON según un JSON Schema provisto. Devuelve únicamente el JSON estructurado solicitado.");
        input.add(sys);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        JsonArray userContent = new JsonArray();

        JsonObject cfg = new JsonObject();
        cfg.addProperty("type", "text");
        cfg.addProperty("text", String.format("Config del plan: Idioma=%s\nNivel=%s\nNumero de quizzes=%d\nPreguntas por quiz=%d\nNumero de flashcards=%d\nNumero de hotTopics=%d\nTitulo=%s",
                language == null ? "" : language,
                level == null ? "" : level,
                quizLimit, questionsLimit, flashcardLimit, hotTopicsLimit,
                title == null ? "" : title));
        userContent.add(cfg);

        JsonObject intro = new JsonObject();
        intro.addProperty("type", "text");
        intro.addProperty("text", "A continuación se proporciona el temario. Usa SOLO su contenido para generar el StudyPlan conforme al JSON Schema.");
        userContent.add(intro);

        JsonObject texto = new JsonObject();
        texto.addProperty("type", "text");
        texto.addProperty("text", extractedText == null ? "" : extractedText);
        userContent.add(texto);

        user.add("content", userContent);
        input.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", "gpt-5.1-mini");
        body.add("response_format", rf);
        body.add("input", input);

        String jsonBody = gson.toJson(body);

        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp == null) throw new IOException("No response from OpenAI");
            String respBody = resp.body() != null ? resp.body().string() : "";
            logRawResponse("generateStudyPlanWithSchema response:", respBody);
            if (!resp.isSuccessful()) {
                throw new OpenAiException(resp.code(), respBody);
            }
            return respBody;
        }
    }
}
