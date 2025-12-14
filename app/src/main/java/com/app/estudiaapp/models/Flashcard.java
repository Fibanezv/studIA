package com.app.estudiaapp.models;

public class Flashcard {
    public String pregunta;
    public String respuesta;
    public boolean completada = false;
    public boolean favorita = false;

    public Flashcard(String pregunta, String respuesta) {
        this.pregunta = pregunta;
        this.respuesta = respuesta;
    }
}

