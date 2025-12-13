package com.app.estudiaapp.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utilidad para validar archivos seleccionados por el usuario antes de procesarlos.
 * Valida tipo MIME, tamaño y legibilidad.
 */
public class FileValidator {

    private static final String TAG = "FileValidator";
    
    // Límite de tamaño: 50 MB
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    
    // Tipos MIME soportados
    private static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf",
            "application/x-pdf",
            "text/plain"  // Para soporte futuro de TXT
    ));

    /**
     * Resultado de validación de archivo
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String mimeType;
        public final long fileSizeBytes;

        private ValidationResult(boolean isValid, String errorMessage, String mimeType, long fileSizeBytes) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.mimeType = mimeType;
            this.fileSizeBytes = fileSizeBytes;
        }

        public static ValidationResult success(String mimeType, long size) {
            return new ValidationResult(true, null, mimeType, size);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null, 0);
        }
    }

    /**
     * Valida un archivo seleccionado por el usuario
     * 
     * @param context Contexto de la aplicación
     * @param uri URI del archivo a validar
     * @return ValidationResult con el resultado de la validación
     */
    @NonNull
    public static ValidationResult validate(@NonNull Context context, @NonNull Uri uri) {
        try {
            // 1. Verificar que el archivo sea legible
            if (!isReadable(context, uri)) {
                Log.w(TAG, "File is not readable: " + uri);
                return ValidationResult.error("No se puede acceder al archivo. Verifica los permisos.");
            }

            // 2. Obtener tipo MIME real
            String mimeType = getMimeType(context, uri);
            if (mimeType == null || mimeType.isEmpty()) {
                Log.w(TAG, "Could not determine MIME type for: " + uri);
                return ValidationResult.error("No se pudo determinar el tipo de archivo.");
            }

            // 3. Validar MIME type contra whitelist
            if (!isSupportedMimeType(mimeType)) {
                Log.w(TAG, "Unsupported MIME type: " + mimeType);
                return ValidationResult.error("Formato no soportado. Solo se admiten archivos PDF.");
            }

            // 4. Obtener y validar tamaño
            long fileSize = getFileSize(context, uri);
            if (fileSize < 0) {
                Log.w(TAG, "Could not determine file size for: " + uri);
                return ValidationResult.error("No se pudo determinar el tamaño del archivo.");
            }

            if (fileSize > MAX_FILE_SIZE_BYTES) {
                long sizeMB = fileSize / (1024 * 1024);
                Log.w(TAG, "File too large: " + sizeMB + " MB (max " + (MAX_FILE_SIZE_BYTES / 1024 / 1024) + " MB)");
                return ValidationResult.error("El archivo es demasiado grande. Máximo permitido: 50 MB.");
            }

            if (fileSize == 0) {
                Log.w(TAG, "File is empty: " + uri);
                return ValidationResult.error("El archivo está vacío.");
            }

            // Todo OK
            Log.i(TAG, "File validated successfully: mimeType=" + mimeType + ", size=" + (fileSize / 1024) + " KB");
            return ValidationResult.success(mimeType, fileSize);

        } catch (Exception e) {
            Log.e(TAG, "Validation failed with exception", e);
            return ValidationResult.error("Error al validar el archivo: " + e.getMessage());
        }
    }

    /**
     * Obtiene el tipo MIME real del archivo usando ContentResolver
     */
    @Nullable
    private static String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        try {
            String mimeType = context.getContentResolver().getType(uri);
            
            // Fallback: intentar obtener de la extensión si ContentResolver falla
            if (mimeType == null || mimeType.isEmpty()) {
                String fileName = getFileName(context, uri);
                if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else if (fileName != null && fileName.toLowerCase().endsWith(".txt")) {
                    mimeType = "text/plain";
                }
            }
            
            return mimeType;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get MIME type", e);
            return null;
        }
    }

    /**
     * Verifica si el tipo MIME está en la lista de soportados
     */
    private static boolean isSupportedMimeType(@Nullable String mimeType) {
        return mimeType != null && SUPPORTED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    /**
     * Obtiene el tamaño del archivo en bytes
     */
    private static long getFileSize(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get file size", e);
        }
        return -1;
    }

    /**
     * Obtiene el nombre del archivo
     */
    @Nullable
    private static String getFileName(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        
        // Fallback
        return uri.getLastPathSegment();
    }

    /**
     * Verifica si el archivo es legible (intenta abrir un InputStream)
     */
    private static boolean isReadable(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            return stream != null;
        } catch (Exception e) {
            Log.w(TAG, "File is not readable", e);
            return false;
        }
    }

    /**
     * Formatea el tamaño de archivo en formato legible (KB, MB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
