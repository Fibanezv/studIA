package com.app.estudiaapp.models;

import java.io.Serializable;

public class QuestionResult implements Serializable {
    public String questionText;
    public String userAnswer;
    public String correctAnswer;
    public boolean isCorrect;

    public QuestionResult() {
    }

    public QuestionResult(String questionText, String userAnswer, String correctAnswer, boolean isCorrect) {
        this.questionText = questionText;
        this.userAnswer = userAnswer;
        this.correctAnswer = correctAnswer;
        this.isCorrect = isCorrect;
    }
}
