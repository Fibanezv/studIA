package com.app.estudiaapp.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper para mostrar diálogos de error de forma consistente en toda la app.
 * Maneja la creación y visualización de AlertDialogs con títulos, mensajes,
 * botones de reintentar (si es retriable) y detalles técnicos en debug.
 */
public class ErrorDialogHelper {

    private static final String TAG = "ErrorDialogHelper";

    /**
     * Muestra un diálogo de error basado en el tipo clasificado
     * 
     * @param context Contexto de Android (Activity)
     * @param errorType Tipo de error a mostrar
     * @param onRetryClick Callback opcional cuando el usuario presiona "Reintentar"
     */
    public static void showErrorDialog(
            @NonNull Context context,
            @NonNull GeneralErrorType errorType,
            @Nullable Runnable onRetryClick) {
        showErrorDialog(context, errorType, null, onRetryClick);
    }

    /**
     * Muestra un diálogo de error basado en la excepción
     * 
     * @param context Contexto de Android (Activity)
     * @param exception Excepción a mostrar
     * @param onRetryClick Callback opcional cuando el usuario presiona "Reintentar"
     */
    public static void showErrorDialog(
            @NonNull Context context,
            @Nullable Exception exception,
            @Nullable Runnable onRetryClick) {
        GeneralErrorType errorType = ErrorHandler.classify(exception);
        showErrorDialog(context, errorType, exception, onRetryClick);
    }

    /**
     * Muestra un diálogo de error completo con manejo de retry y debug details
     * 
     * @param context Contexto de Android (Activity)
     * @param errorType Tipo de error a mostrar
     * @param exception Excepción original (opcional, para debug details)
     * @param onRetryClick Callback opcional cuando el usuario presiona "Reintentar"
     */
    public static void showErrorDialog(
            @NonNull Context context,
            @NonNull GeneralErrorType errorType,
            @Nullable Exception exception,
            @Nullable Runnable onRetryClick) {

        Log.w(TAG, "Showing error dialog: " + errorType.name() + " - " + errorType.getUserMessage());

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(errorType.getDialogTitle())
                .setMessage(errorType.getUserMessage())
                .setCancelable(false);

        // Botón de Cancelar/Cerrar
        builder.setNegativeButton("Cerrar", (dialog, which) -> dialog.dismiss());

        // Botón de Reintentar si el error es retriable
        if (errorType.isRetriable() && onRetryClick != null) {
            builder.setPositiveButton("Reintentar", (dialog, which) -> {
                dialog.dismiss();
                onRetryClick.run();
            });
        }

        // Botón para ver detalles técnicos en modo debug (verificar si está en debug mode)
        boolean isDebug = false;
        try {
            Class<?> bc = Class.forName("com.app.estudiaapp.BuildConfig");
            isDebug = bc.getField("DEBUG").getBoolean(null);
        } catch (Exception ignore) {}
        
        if (isDebug && exception != null) {
            builder.setNeutralButton("Ver Detalles", (dialog, which) -> {
                showDebugDetailsDialog(context, errorType, exception);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Muestra un diálogo con detalles técnicos para debugging (solo en DEBUG mode)
     */
    private static void showDebugDetailsDialog(
            @NonNull Context context,
            @NonNull GeneralErrorType errorType,
            @Nullable Exception exception) {

        String message = "Tipo de error: " + errorType.name() + "\n\n";

        if (exception != null) {
            message += "Excepción: " + exception.getClass().getSimpleName() + "\n";
            message += "Mensaje: " + exception.getMessage() + "\n";
            
            if (exception.getCause() != null) {
                message += "Causa: " + exception.getCause().toString() + "\n";
            }

            // Stack trace (primeras líneas)
            StackTraceElement[] stackTrace = exception.getStackTrace();
            if (stackTrace.length > 0) {
                message += "\nStack trace (primeras 3 líneas):\n";
                for (int i = 0; i < Math.min(3, stackTrace.length); i++) {
                    message += "  " + stackTrace[i].toString() + "\n";
                }
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Detalles Técnicos (Debug)")
                .setMessage(message)
                .setPositiveButton("Cerrar", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    /**
     * Muestra un Toast simple para errores no-críticos (sin UI bloqueo)
     * Útil para operaciones de background que no requieren user confirmation
     */
    public static void showErrorToast(
            @NonNull Context context,
            @NonNull GeneralErrorType errorType) {
        android.widget.Toast.makeText(
                context,
                errorType.getUserMessage(),
                android.widget.Toast.LENGTH_LONG
        ).show();

        Log.w(TAG, "Showing error toast: " + errorType.name());
    }

    /**
     * Muestra un Toast basado en una excepción
     */
    public static void showErrorToast(
            @NonNull Context context,
            @Nullable Exception exception) {
        GeneralErrorType errorType = ErrorHandler.classify(exception);
        showErrorToast(context, errorType);
    }
}
