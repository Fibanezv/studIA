package com.app.estudiaapp.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa un Quiz dentro de un StudyPlan. Contiene un id, título, lista de preguntas
 * y el progreso específico de este quiz.
 */
public class Quiz {
    public String id;
    public String titulo;
    public String typology;
    public List<Question> questions;
    public QuizProgress progreso;

    public Quiz() {
        this.questions = new ArrayList<>();
        this.progreso = new QuizProgress();
    }

    public Quiz(String id, String titulo) {
        this.id = id;
        this.titulo = titulo;
        this.questions = new ArrayList<>();
        this.progreso = new QuizProgress();
    }
}
