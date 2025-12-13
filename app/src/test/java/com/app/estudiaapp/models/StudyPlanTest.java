package com.app.estudiaapp.models;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import org.junit.Test;

public class StudyPlanTest {

    @Test
    public void gsonRoundTrip_preservesCoreFields() {
        StudyPlan plan = new StudyPlan("U00001", "Plan de prueba");
        plan.quizzes = new ArrayList<>();
        plan.flashcards = new ArrayList<>();

        Gson gson = new Gson();
        String json = gson.toJson(plan);
        StudyPlan back = gson.fromJson(json, StudyPlan.class);

        assertEquals("U00001", back.id);
        assertEquals("Plan de prueba", back.nombre);
        assertNotNull(back.quizzes);
        assertNotNull(back.flashcards);
    }

    @Test
    public void defaultLists_areNonNull() {
        StudyPlan plan = new StudyPlan("U00002", "Otro plan");
        assertNotNull(plan.quizzes);
        assertNotNull(plan.flashcards);
        assertNotNull(plan.hotTopics);
    }
}
