package com.app.estudiaapp.models;

/**
 * Simple model representing a highlighted topic extracted from a plan's PDF.
 */
public class HotTopic {
    public String title;
    public String description;

    public HotTopic() {}

    public HotTopic(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
