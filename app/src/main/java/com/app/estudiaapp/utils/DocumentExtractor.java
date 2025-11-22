package com.app.estudiaapp.utils;

import android.content.Context;
import android.net.Uri;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import android.util.SparseArray;

public class DocumentExtractor {

    private static final String TAG = "DocumentExtractor";
    public static String extractTextFromUri(Context ctx, Uri uri) throws IOException {
        String mime = ctx.getContentResolver().getType(uri);
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) return "";
        try {
            if (mime != null && mime.equals("application/pdf")) {
                // Use PdfRenderer + OCR for PDFs to avoid PDFBox resource/packaging issues.
                try { in.close(); } catch (Exception ignored) {}
                return ocrPdfWithPdfRenderer(ctx, uri);
            } else {
                // Other document types: try a plain text read from the stream
                try {
                    String txt = streamToString(in);
                    return txt != null ? txt : "";
                } catch (Exception e) {
                    return "";
                }
            }
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    

    /**
     * Fallback: render each page with PdfRenderer and run ML Kit OCR on the bitmaps.
     * This is slower but works for scanned PDFs where text streams are not present.
     */
    private static String ocrPdfWithPdfRenderer(android.content.Context ctx, Uri uri) {
        StringBuilder out = new StringBuilder();
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        try {
            pfd = ctx.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return "";
            renderer = new PdfRenderer(pfd);
            int pageCount = renderer.getPageCount();
            TextRecognizer recognizer = new TextRecognizer.Builder(ctx).build();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = null;
                try {
                    page = renderer.openPage(i);
                    int w = page.getWidth();
                    int h = page.getHeight();
                    // limit bitmap size to avoid OOM on large pages
                    final int MAX_DIM = 2048;
                    float scale = Math.min(1f, (float) MAX_DIM / Math.max(w, h));
                    int bmpW = Math.max(1, (int) (w * scale));
                    int bmpH = Math.max(1, (int) (h * scale));
                    Bitmap bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    Frame frame = new Frame.Builder().setBitmap(bmp).build();
                    SparseArray<TextBlock> blocks = recognizer.detect(frame);
                    StringBuilder pageText = new StringBuilder();
                    for (int b = 0; b < blocks.size(); b++) {
                        TextBlock tb = blocks.valueAt(b);
                        if (tb != null && tb.getValue() != null && !tb.getValue().trim().isEmpty()) {
                            pageText.append(tb.getValue()).append('\n');
                        }
                    }
                    String txt = pageText.toString();
                    if (!txt.trim().isEmpty()) out.append(txt).append('\n').append('\n');
                    // recycle bitmap
                    try { bmp.recycle(); } catch (Exception ignored) {}
                } catch (Exception e) {
                    android.util.Log.w(TAG, "OCR page failed: " + e.getMessage());
                } finally {
                    try { if (page != null) page.close(); } catch (Exception ignored) {}
                }
            }
            try { recognizer.release(); } catch (Exception ignored) {}
        } catch (Exception e) {
            android.util.Log.w(TAG, "PdfRenderer/OCR fallback failed: " + e.getMessage(), e);
        } finally {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    private static String streamToString(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
