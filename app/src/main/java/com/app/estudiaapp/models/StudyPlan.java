package com.app.estudiaapp.models;

import java.util.ArrayList;
import java.util.List;

/**
 * StudyPlan actualizado para soportar múltiples quizzes.
 * Antes existía un campo `cuestionarios` (lista plana de preguntas). Ahora cada plan
 * contiene una lista de `Quiz` (cada uno con sus preguntas y su propio progreso).
 */
public class StudyPlan {
    public String id;
    public String nombre;
    public List<Quiz> quizzes;
    public List<Flashcard> flashcards;
    public List<HotTopic> hotTopics;
    public String sourceUrl;
    public UserProgress progreso;

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
}

