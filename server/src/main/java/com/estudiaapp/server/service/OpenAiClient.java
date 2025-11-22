package com.estudiaapp.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OpenAiClient {
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client;
    private final String apiKey;

    public OpenAiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String requestPlan(String systemPrompt, String userPrompt) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", "gpt-4o-mini");
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", userPrompt);
        messages.add(sys);
        messages.add(usr);
        payload.add("messages", messages);
        payload.addProperty("temperature", 0.0);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request req = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("OpenAI error: " + resp.code() + " " + resp.body().string());
            String txt = resp.body().string();
            // parse assistant content
            JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject first = choices.get(0).getAsJsonObject();
                JsonObject message = first.getAsJsonObject("message");
                if (message != null && message.has("content")) return message.get("content").getAsString();
            }
            return txt;
        }
    }
}
