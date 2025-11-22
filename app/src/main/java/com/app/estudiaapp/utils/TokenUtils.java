package com.app.estudiaapp.utils;

public class TokenUtils {
    // Conservative heuristic: assume ~4 characters per token on average
    public static int estimateTokens(String text) {
        if (text == null || text.length() == 0) return 0;
        return Math.max(1, (int)Math.ceil((double)text.length() / 4.0));
    }

    // Helper to decide if we should chunk based on a model token limit
    public static boolean needsChunking(String text, int modelMaxTokens) {
        int est = estimateTokens(text);
        // use a safety margin (80%) to avoid hitting the hard limit
        return est > (int)(modelMaxTokens * 0.8);
    }
}
