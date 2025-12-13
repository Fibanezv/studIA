package com.app.estudiaapp.models;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuizTest {

    @Test
    public void optionsContainAnswer_trueFalse() {
        Question q = new Question();
        q.options = Arrays.asList("Verdadero", "Falso");
        q.answer = "Verdadero";

        assertEquals(2, q.options.size());
        assertTrue(q.options.contains(q.answer));
    }

    @Test
    public void optionsContainAnswer_abcd() {
        Question q = new Question();
        q.options = Arrays.asList("A", "B", "C", "D");
        q.answer = "C";

        assertEquals(4, q.options.size());
        assertTrue(q.options.contains(q.answer));
    }
}
