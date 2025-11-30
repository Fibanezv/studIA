package com.app.estudiaapp.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * StudyPlan actualizado para soportar múltiples quizzes.
 * Antes existía un campo `cuestionarios` (lista plana de preguntas). Ahora cada plan
 * contiene una lista de `Quiz` (cada uno con sus preguntas y su propio progreso).
 */
public class StudyPlan {
    public String id;
    public String nombre;
    public String description; // NEW: Organic description of the plan content
    public List<Quiz> quizzes;
    public List<Flashcard> flashcards;
    public List<HotTopic> hotTopics;
    public String sourceUrl;
    public UserProgress progreso;
    
    // NEW: Generation status fields
    public String status; // "generating", "completed", "error", "partial"
    public Map<String, Object> generationProgress; // Store as Map for flexibility

    public StudyPlan(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
        this.quizzes = new ArrayList<>();
        this.flashcards = new ArrayList<>();
        this.hotTopics = new ArrayList<>();
        this.sourceUrl = null;
        this.progreso = new UserProgress();
    }

    // Conveniencia: obtener todas las preguntas del plan en una lista plana.
    // Útil temporalmente para compatibilidad con código que esperaba `cuestionarios`.
    public List<Question> obtenerTodasLasPreguntas() {
        List<Question> out = new ArrayList<>();
        if (quizzes == null) return out;
        for (Quiz qz : quizzes) {
            if (qz != null && qz.questions != null) out.addAll(qz.questions);
        }
        return out;
    }
    
    // Helper to check if plan is still generating
    public boolean isGenerating() {
        return "generating".equals(status);
    }
    
    // Helper to get generation progress percentage
    public int getGenerationPercentage() {
        if (generationProgress == null) return 0;
        Object pctObj = generationProgress.get("percentage");
        if (pctObj instanceof Number) {
            return ((Number) pctObj).intValue();
        }
        // Fallback: calculate from batches
        Object totalObj = generationProgress.get("totalBatches");
        Object completedObj = generationProgress.get("completedBatches");
        if (totalObj instanceof Number && completedObj instanceof Number) {
            int total = ((Number) totalObj).intValue();
            int completed = ((Number) completedObj).intValue();
            if (total == 0) return 0;
            return (int) ((completed * 100.0) / total);
        }
        return 0;
    }
}

