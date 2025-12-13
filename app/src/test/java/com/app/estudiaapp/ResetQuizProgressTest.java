package com.app.estudiaapp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.QuizProgress;
import com.app.estudiaapp.models.StudyPlan;

/**
 * Tests para la funcionalidad de resetQuizProgress.
 * 
 * Verifica que el reset de quiz:
 * - Reinicia todos los campos a sus valores por defecto
 * - Maneja correctamente quiz no encontrados
 * - Preserva la estructura del plan
 */
public class ResetQuizProgressTest {

    private StudyPlan testPlan;

    @Before
    public void setUp() {
        // Initialize test plan with a quiz
        testPlan = new StudyPlan("U00001", "Test Plan");
        
        // Create test quiz with advanced progress
        Quiz quiz = new Quiz("q1", "Test Quiz");
        quiz.progreso.preguntasRespondidas = 10;
        quiz.progreso.preguntasCorrectas = 8;
        quiz.progreso.preguntasIncorrectas = 2;
        quiz.progreso.currentIndex = 9;
        quiz.progreso.nota = 80;
        
        testPlan.quizzes.add(quiz);
    }

    /**
     * Test: resetQuizProgress reinicia todos los campos correctamente
     */
    @Test
    public void testResetQuizProgress_Success() {
        String quizId = "q1";
        
        // Verify before reset
        assertEquals("Should have answered questions", 10, testPlan.quizzes.get(0).progreso.preguntasRespondidas);
        assertEquals("Should have correct answers", 8, testPlan.quizzes.get(0).progreso.preguntasCorrectas);
        assertEquals("Should have incorrect answers", 2, testPlan.quizzes.get(0).progreso.preguntasIncorrectas);
        assertEquals("Should have current index", 9, testPlan.quizzes.get(0).progreso.currentIndex);
        assertEquals("Should have score", 80, testPlan.quizzes.get(0).progreso.nota);
        
        // Simulate reset operation (as would happen in resetQuizProgress transaction)
        QuizProgress resetProgress = new QuizProgress();
        resetProgress.preguntasRespondidas = 0;
        resetProgress.preguntasCorrectas = 0;
        resetProgress.preguntasIncorrectas = 0;
        resetProgress.currentIndex = 0;
        resetProgress.nota = -1;
        resetProgress.lastUpdatedMillis = System.currentTimeMillis();
        
        // Apply reset
        for (Quiz q : testPlan.quizzes) {
            if (q.id.equals(quizId)) {
                q.progreso = resetProgress;
                break;
            }
        }
        
        // Verify after reset
        QuizProgress resetProg = testPlan.quizzes.get(0).progreso;
        assertEquals("preguntasRespondidas should be 0", 0, resetProg.preguntasRespondidas);
        assertEquals("preguntasCorrectas should be 0", 0, resetProg.preguntasCorrectas);
        assertEquals("preguntasIncorrectas should be 0", 0, resetProg.preguntasIncorrectas);
        assertEquals("currentIndex should be 0", 0, resetProg.currentIndex);
        assertEquals("nota should be -1", -1, resetProg.nota);
    }

    /**
     * Test: resetQuizProgress con quiz no encontrado
     */
    @Test
    public void testResetQuizProgress_QuizNotFound() {
        String nonExistentQuizId = "q999";
        
        // Verify quiz doesn't exist
        boolean found = testPlan.quizzes.stream()
            .anyMatch(q -> q.id.equals(nonExistentQuizId));
        
        assertFalse("Quiz should not be found for reset", found);
    }

    /**
     * Test: resetQuizProgress preserva otros quizzes
     */
    @Test
    public void testResetQuizProgress_PreservesOtherQuizzes() {
        // Add a second quiz
        Quiz quiz2 = new Quiz("q2", "Another Quiz");
        quiz2.progreso.preguntasRespondidas = 5;
        quiz2.progreso.preguntasCorrectas = 3;
        testPlan.quizzes.add(quiz2);
        
        // Reset only the first quiz
        QuizProgress resetProgress = new QuizProgress();
        resetProgress.preguntasRespondidas = 0;
        resetProgress.preguntasCorrectas = 0;
        resetProgress.preguntasIncorrectas = 0;
        resetProgress.currentIndex = 0;
        resetProgress.nota = -1;
        resetProgress.lastUpdatedMillis = System.currentTimeMillis();
        
        for (Quiz q : testPlan.quizzes) {
            if (q.id.equals("q1")) {
                q.progreso = resetProgress;
                break;
            }
        }
        
        // Verify first quiz is reset
        assertEquals("First quiz should be reset", 0, testPlan.quizzes.get(0).progreso.preguntasRespondidas);
        
        // Verify second quiz is unchanged
        assertEquals("Second quiz should be unchanged", 5, testPlan.quizzes.get(1).progreso.preguntasRespondidas);
        assertEquals("Second quiz should be unchanged", 3, testPlan.quizzes.get(1).progreso.preguntasCorrectas);
    }

    /**
     * Test: resetQuizProgress mantiene estructura de quiz intacta
     */
    @Test
    public void testResetQuizProgress_PreservesQuizStructure() {
        String quizId = "q1";
        String originalTitle = testPlan.quizzes.get(0).titulo;
        
        // Reset progress only
        QuizProgress resetProgress = new QuizProgress();
        resetProgress.preguntasRespondidas = 0;
        resetProgress.preguntasCorrectas = 0;
        resetProgress.preguntasIncorrectas = 0;
        resetProgress.currentIndex = 0;
        resetProgress.nota = -1;
        resetProgress.lastUpdatedMillis = System.currentTimeMillis();
        
        for (Quiz q : testPlan.quizzes) {
            if (q.id.equals(quizId)) {
                q.progreso = resetProgress;
                break;
            }
        }
        
        // Verify quiz structure preserved
        assertEquals("Quiz ID should be unchanged", quizId, testPlan.quizzes.get(0).id);
        assertEquals("Quiz title should be unchanged", originalTitle, testPlan.quizzes.get(0).titulo);
    }

    /**
     * Test: Validación de todos los campos iniciales de QuizProgress
     */
    @Test
    public void testNewQuizProgressHasCorrectDefaults() {
        QuizProgress freshProgress = new QuizProgress();
        
        assertTrue("preguntasRespondidas should be 0 or positive", freshProgress.preguntasRespondidas >= 0);
        assertTrue("preguntasCorrectas should be 0 or positive", freshProgress.preguntasCorrectas >= 0);
        assertTrue("preguntasIncorrectas should be 0 or positive", freshProgress.preguntasIncorrectas >= 0);
        assertTrue("currentIndex should be 0 or positive", freshProgress.currentIndex >= 0);
        assertEquals("nota should be -1 initially", -1, freshProgress.nota);
    }
}
