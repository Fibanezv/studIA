package com.app.estudiaapp.ui;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small lifecycle-aware overlay manager that tracks nested show/hide calls
 * via a reference count to avoid unbalanced hide calls, and provides
 * a cancel callback binding for the overlay cancel button.
 */
public class OverlayManager {
    private final WeakReference<Activity> activityRef;
    private final int overlayId;
    private final int messageId;
    private final int cancelButtonId;
    private final AtomicInteger counter = new AtomicInteger(0);

    public OverlayManager(Activity activity, int overlayId, int messageId, int cancelButtonId) {
        this.activityRef = new WeakReference<>(activity);
        this.overlayId = overlayId;
        this.messageId = messageId;
        this.cancelButtonId = cancelButtonId;
    }

    public void show(String message, Runnable onCancel) {
        Activity a = activityRef.get();
        if (a == null) return;
        a.runOnUiThread(() -> {
            View overlay = overlayId != 0 ? a.findViewById(overlayId) : null;
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            TextView tv = messageId != 0 ? (TextView) a.findViewById(messageId) : null;
            if (tv != null && message != null) tv.setText(message);
            // bind cancel only if provided (don't overwrite existing listener when null)
            if (cancelButtonId != 0) {
                View cancel = a.findViewById(cancelButtonId);
                if (cancel != null) {
                    if (onCancel != null) {
                        cancel.setVisibility(View.VISIBLE);
                        cancel.setOnClickListener(v -> {
                            try { onCancel.run(); } catch (Exception ignored) {}
                        });
                    } else {
                        cancel.setVisibility(View.GONE);
                        cancel.setOnClickListener(null);
                    }
                }
            }
            counter.incrementAndGet();
        });
    }

    public void hide() {
        Activity a = activityRef.get();
        if (a == null) return;
        int val = counter.decrementAndGet();
        if (val > 0) return; // other callers still expect overlay visible
        a.runOnUiThread(() -> {
            View overlay = overlayId != 0 ? a.findViewById(overlayId) : null;
            if (overlay != null) overlay.setVisibility(View.GONE);
        });
    }

    /**
     * Force hide and reset reference count.
     */
    public void forceHide() {
        counter.set(0);
        Activity a = activityRef.get();
        if (a == null) return;
        a.runOnUiThread(() -> {
            View overlay = overlayId != 0 ? a.findViewById(overlayId) : null;
            if (overlay != null) overlay.setVisibility(View.GONE);
        });
    }
}
