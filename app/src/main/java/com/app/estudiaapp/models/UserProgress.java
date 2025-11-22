package com.app.estudiaapp.models;

public class UserProgress {
    public int preguntasRespondidas = 0;
    public int preguntasCorrectas = 0;
    public int flashcardsEstudiadas = 0;
    public int nivel = 1;
    // Additional metadata (populated by OpenAI parsing)
    public String difficulty = null; // e.g., "easy", "medium", "hard" or localized string
    public int estimatedStudyTimeMinutes = 0; // approximate total minutes recommended

    public void actualizarProgreso(boolean acierto) {
        preguntasRespondidas++;
        if (acierto) preguntasCorrectas++;
        if (preguntasRespondidas >= 10) nivel++;
    }

    /**
     * Registrar que una flashcard ha sido estudiada. Se asegura de no superar el total
     * proporcionado para evitar conteos inconsistentes.
     */
    public void registrarFlashcardEstudiada(int totalFlashcards) {
        if (totalFlashcards <= 0) return;
        if (flashcardsEstudiadas < totalFlashcards) flashcardsEstudiadas++;
    }

    /**
     * Obtener porcentaje de flashcards estudiadas (0..100).
     */
    public int obtenerPorcentajeFlashcards(int totalFlashcards) {
        if (totalFlashcards <= 0) return 0;
        return (int) ((flashcardsEstudiadas * 100.0f) / totalFlashcards);
    }
}
