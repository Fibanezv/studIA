package com.app.estudiaapp.models;

import java.util.List;

public class Question {
    public String question;
    public List<String> options;
    public String answer;

    public Question() {
        // Constructor vacío para GSON
    }

    public Question(String question, List<String> options, String answer) {
        this.question = question;
        this.options = options;
        this.answer = answer;
    }
}

