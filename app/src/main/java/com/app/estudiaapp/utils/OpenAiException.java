package com.app.estudiaapp.utils;

import java.io.IOException;

public class OpenAiException extends IOException {
    private final int statusCode;
    private final String responseBody;

    public OpenAiException(int statusCode, String responseBody) {
        super("OpenAI error: code=" + statusCode + " body=" + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        // Retry on 5xx or 429 (rate limit). Do not retry on 4xx client errors like context_length_exceeded (400).
        return (statusCode >= 500 && statusCode < 600) || statusCode == 429;
    }
}
