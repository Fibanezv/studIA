package com.app.estudiaapp.models;

import com.app.estudiaapp.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized badge rules and factory. Given a StudyPlan, returns available badges with their achieved state.
 */
public class BadgesRepository {

    public static List<Badge> getBadgesForPlan(StudyPlan plan) {
        List<Badge> result = new ArrayList<>();
        if (plan == null) return result;

        int totalFlash = (plan.flashcards == null) ? 0 : plan.flashcards.size();
        int studiedFlash = (plan.progreso == null) ? 0 : plan.progreso.flashcardsEstudiadas;

        int totalQuizzes = (plan.quizzes == null) ? 0 : plan.quizzes.size();
        int completedQuizzes = 0;
        int totalQuestions = 0;
    int totalAnswered = 0;
    int totalCorrect = 0;
    boolean anyPerfect = false;
    int perfectQuizzesCount = 0;

        if (plan.quizzes != null) {
            for (Quiz q : plan.quizzes) {
                int tq = (q.questions == null) ? 0 : q.questions.size();
                totalQuestions += tq;
                int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
                int correct = (q.progreso == null) ? 0 : q.progreso.preguntasCorrectas;
                totalAnswered += answered;
                totalCorrect += correct;
                if (tq > 0 && answered >= tq) completedQuizzes++;
                if (tq > 0 && correct >= tq) {
                    anyPerfect = true;
                    perfectQuizzesCount++;
                }
            }
        }

    // Create a variety of badges. For now all use the same perfect icon; achieved is computed per rule.
    // id is stable key for each badge
    result.add(new Badge("responder_10", "Responder 10 preguntas", (totalAnswered >= 10), R.drawable.ic_responder_10));
    result.add(new Badge("responder_20", "Responder 20 preguntas", (totalAnswered >= 20), R.drawable.ic_responder_20));
    result.add(new Badge("responder_50", "Responder 50 preguntas", (totalAnswered >= 50), R.drawable.ic_responder_50));
    result.add(new Badge("responder_100", "Responder 100 preguntas", (totalAnswered >= 100), R.drawable.ic_responder_100));

    result.add(new Badge("acierta_20", "Acierta 20 preguntas", (totalCorrect >= 20), R.drawable.ic_question_ok_20));


    result.add(new Badge("accuracy_75", "Precisión >= 75%", (totalAnswered >= 20 && ((totalCorrect * 100) / totalAnswered) >= 75), R.drawable.ic_questions_acc_75));
    result.add(new Badge("accuracy_100", "Precisión >= 100%", (totalAnswered >= 50 && ((totalCorrect * 100) / totalAnswered) >= 100), R.drawable.ic_questions_acc_100));

    result.add(new Badge("completa_1_quiz", "Completar 1 quiz", (completedQuizzes >= 1), R.drawable.ic_quizz_ok_1));
    result.add(new Badge("completa_3_quizzes", "Completar 3 quizzes", (completedQuizzes >= 3), R.drawable.ic_quizz_ok_3));
    result.add(new Badge("completa_todos_quizzes", "Completar todos los quizzes", (totalQuizzes > 0 && completedQuizzes == totalQuizzes), R.drawable.ic_quizz_ok_all));

    result.add(new Badge("quiz_perfecto", "Quiz perfecto", anyPerfect, R.drawable.ic_quiz_perfecto));
    result.add(new Badge("perfect_2_quizzes", "2 quizzes perfectos", (perfectQuizzesCount >= 2), R.drawable.ic_quiz_perfecto_2));

    result.add(new Badge("estudia_10_flash", "Estudia 10 flashcards", (studiedFlash >= 10), R.drawable.ic_study_10_flash));
    result.add(new Badge("estudia_mitad_flash", "Estudia la mitad de las flashcards", (totalFlash > 0 && studiedFlash >= (totalFlash / 2)), R.drawable.ic_study_50perc_flash));
    result.add(new Badge("estudia_todas_flash", "Estudia todas las flashcards", (totalFlash > 0 && studiedFlash == totalFlash), R.drawable.ic_study_100perc_flash));

        return result;
    }
}
