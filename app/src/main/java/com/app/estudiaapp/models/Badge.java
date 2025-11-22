package com.app.estudiaapp.models;

/**
 * Simple model representing an achievement badge.
 */
public class Badge {
    public String id;
    public String title;
    public boolean achieved;
    public int iconRes;

    public Badge(String id, String title, boolean achieved, int iconRes) {
        this.id = id;
        this.title = title;
        this.achieved = achieved;
        this.iconRes = iconRes;
    }
}
