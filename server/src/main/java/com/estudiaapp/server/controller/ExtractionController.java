package com.estudiaapp.server.controller;

import com.estudiaapp.server.service.ExtractionService;
import com.google.gson.JsonObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExtractionController {

    private final ExtractionService extractionService;

    public ExtractionController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping("/extract-process")
    public ResponseEntity<?> extractAndProcess(@RequestBody Map<String, Object> body) {
        try {
            String fileUrl = (String) body.get("fileUrl");
            String uid = (String) body.get("uid");
            String planId = (String) body.get("planId");
            Map<String, Object> constraints = (Map<String, Object>) body.get("constraints");

            JsonObject result = extractionService.extractProcessAndSave(fileUrl, uid, planId, constraints);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }
}
