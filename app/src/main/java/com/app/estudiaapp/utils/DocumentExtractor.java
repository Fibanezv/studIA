package com.app.estudiaapp.utils;

/**
 * DocumentExtractor was removed from the project. Text extraction (OCR/PDF parsing)
 * is now expected to happen outside of this module and the worker must receive
 * pre-extracted text via the input data key `StudyPlanWorker.INPUT_EXTRACTED_TEXT`.
 *
 * This stub exists to make any accidental references fail fast with a clear message.
 */
@Deprecated
public final class DocumentExtractor {
    private DocumentExtractor() {}

    public static String extractTextFromUri(android.content.Context ctx, android.net.Uri uri) {
        throw new UnsupportedOperationException("DocumentExtractor removed. Provide pre-extracted text via StudyPlanWorker.INPUT_EXTRACTED_TEXT.");
    }
}
