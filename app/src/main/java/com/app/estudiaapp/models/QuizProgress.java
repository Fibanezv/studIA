package com.app.estudiaapp.models;

/**
 * Progreso por cada Quiz. Se mantiene separado del UserProgress global para permitir
 * trazabilidad por quiz.
 */
public class QuizProgress {
    public int preguntasRespondidas = 0;
    public int preguntasCorrectas = 0;
    public int preguntasIncorrectas = 0;
    public long lastUpdatedMillis = 0;
    // índice (0-based) de la siguiente pregunta a responder. Permite reanudar el quiz.
    public int currentIndex = 0;
    // Nota (porcentaje) obtenida al completar el quiz. -1 indica sin nota registrada.
    public int nota = -1;

    public QuizProgress() {}

    public void actualizar(boolean acierto) {
        // Para compatibilidad, delegar a la versión que no conoce el total
        preguntasRespondidas++;
        if (acierto) preguntasCorrectas++;
        else preguntasIncorrectas++;
        lastUpdatedMillis = System.currentTimeMillis();
    }

    /**
     * Actualiza el progreso teniendo en cuenta el número total de preguntas y evitando
     * que el número de preguntas respondidas supere el total.
     */
    public void actualizar(boolean acierto, int totalPreguntas) {
        if (preguntasRespondidas < totalPreguntas) {
            preguntasRespondidas++;
            if (acierto) preguntasCorrectas++;
            else preguntasIncorrectas++;
            lastUpdatedMillis = System.currentTimeMillis();
        } else {
            // ya alcanzado el total; no contamos más respuestas
            lastUpdatedMillis = System.currentTimeMillis();
        }
        // Asegurar invariantes: no exceder total
        if (preguntasRespondidas > totalPreguntas) preguntasRespondidas = totalPreguntas;
        if (preguntasCorrectas > totalPreguntas) preguntasCorrectas = totalPreguntas;
        if (preguntasIncorrectas > totalPreguntas) preguntasIncorrectas = totalPreguntas;
        // Ajuste adicional: si por cualquier razón suma > total, reducir incorrectas
        int suma = preguntasCorrectas + preguntasIncorrectas;
        if (suma > totalPreguntas) {
            // recortar incorrectas preferentemente
            preguntasIncorrectas = Math.max(0, totalPreguntas - preguntasCorrectas);
        }
    }
}
