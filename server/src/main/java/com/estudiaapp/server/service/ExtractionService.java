package com.estudiaapp.server.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExtractionService {

    private final OpenAiClient openAiClient;

    public ExtractionService() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null) throw new RuntimeException("OPENAI_API_KEY env var not set");
        this.openAiClient = new OpenAiClient(key);
    }

    public JsonObject extractProcessAndSave(String fileUrl, String uid, String planId, Map<String, Object> constraints) throws Exception {
        String text = downloadAndExtract(fileUrl);

        // build prompts
        String systemPrompt = "You must only return a single JSON object following the schema exactly: {\n  \"nombre\": string,\n  \"flashcards\": [{\"pregunta\": string, \"respuesta\": string}],\n  \"quizzes\": [{\"id\": string, \"titulo\": string, \"questions\": [{\"question\": string, \"options\": [string], \"answer\": string}]}],\n  \"hotTopics\": [{\"title\": string, \"description\": string}]\n} and nothing else. Ensure counts match constraints when provided.\n";
        String userPrompt = "Generate a study plan using the following document text:\n" + text + "\nConstraints (JSON): " + new com.google.gson.Gson().toJson(constraints) + "\nReturn only the JSON object.";

        String aiResp = openAiClient.requestPlan(systemPrompt, userPrompt);

        // try parse
        JsonObject saved = new JsonObject();
        saved.addProperty("raw_response", aiResp);
        saved.addProperty("from_server_extraction", true);
        saved.addProperty("source_url", fileUrl);

        boolean parsed = false;
        try {
            JsonElement el = JsonParser.parseString(aiResp.trim());
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                // write structured fields into Firestore doc
                Map<String, Object> toSave = new HashMap<>();
                toSave.put("nombre", obj.has("nombre") ? obj.get("nombre").getAsString() : "Plan desde documento");
                toSave.put("raw_response", aiResp);
                toSave.put("from_server_extraction", true);
                toSave.put("source_url", fileUrl);
                toSave.put("status", "processed");
                toSave.put("createdAt", com.google.firebase.Timestamp.now());
                if (obj.has("flashcards")) toSave.put("flashcards", new com.google.gson.Gson().fromJson(obj.get("flashcards"), Object.class));
                if (obj.has("quizzes")) toSave.put("quizzes", new com.google.gson.Gson().fromJson(obj.get("quizzes"), Object.class));
                if (obj.has("hotTopics")) toSave.put("hotTopics", new com.google.gson.Gson().fromJson(obj.get("hotTopics"), Object.class));

                Firestore db = FirestoreClient.getFirestore();
                DocumentReference docRef = db.collection("users").document(uid).collection("plans").document(planId);
                ApiFuture<WriteResult> future = docRef.set(toSave);
                future.get();
                parsed = true;
                saved.addProperty("status", "processed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            saved.addProperty("status", "needs_review");
        }

        if (!parsed) {
            // save fallback doc with raw_response and flag
            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw_response", aiResp);
            fallback.put("from_server_extraction", true);
            fallback.put("source_url", fileUrl);
            fallback.put("status", "needs_review");
            fallback.put("createdAt", com.google.firebase.Timestamp.now());
            DocumentReference docRef = db.collection("users").document(uid).collection("plans").document(planId);
            docRef.set(fallback).get();
        }

        return saved;
    }

    private String downloadAndExtract(String fileUrl) throws IOException, TikaException, SAXException {
        System.out.println("Downloading: " + fileUrl);
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "EstudiaApp-Extraction/1.0");

        try (InputStream in = conn.getInputStream()) {
            // Tika extraction
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(in, handler, metadata, context);
            String content = handler.toString();
            System.out.println("Extracted length: " + (content == null ? 0 : content.length()));
            return content != null ? content : "";
        }
    }
}
