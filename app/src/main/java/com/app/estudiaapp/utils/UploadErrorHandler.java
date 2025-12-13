package com.app.estudiaapp.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.storage.StorageException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Helper para clasificar excepciones de upload y determinar acciones apropiadas.
 */
public class UploadErrorHandler {

    private static final String TAG = "UploadErrorHandler";

    /**
     * Clasifica una excepción en un UploadErrorType
     * 
     * @param exception La excepción a clasificar
     * @return El tipo de error correspondiente
     */
    @NonNull
    public static UploadErrorType classify(@Nullable Exception exception) {
        if (exception == null) {
            return UploadErrorType.UNKNOWN;
        }

        Log.w(TAG, "Classifying exception: " + exception.getClass().getSimpleName() + " - " + exception.getMessage());

        // Errores de red
        if (exception instanceof FirebaseNetworkException 
                || exception instanceof IOException 
                || exception.getCause() instanceof IOException) {
            return UploadErrorType.NETWORK_ERROR;
        }

        // Timeouts
        if (exception instanceof TimeoutException 
                || exception instanceof SocketTimeoutException
                || exception.getCause() instanceof TimeoutException) {
            return UploadErrorType.TIMEOUT;
        }

        // Errores de autenticación
        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authEx = (FirebaseAuthException) exception;
            String errorCode = authEx.getErrorCode();
            if ("ERROR_PERMISSION_DENIED".equals(errorCode)) {
                return UploadErrorType.PERMISSION_DENIED;
            }
            return UploadErrorType.AUTH_ERROR;
        }

        // Errores de Storage
        if (exception instanceof StorageException) {
            StorageException storageEx = (StorageException) exception;
            int errorCode = storageEx.getErrorCode();
            
            // 403: Permission denied
            if (errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                return UploadErrorType.PERMISSION_DENIED;
            }
            // 408: Request timeout
            if (errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED) {
                return UploadErrorType.TIMEOUT;
            }
            // Otros errores de storage
            return UploadErrorType.STORAGE_UPLOAD_FAILED;
        }

        // Errores de Cloud Functions
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException funcEx = (FirebaseFunctionsException) exception;
            FirebaseFunctionsException.Code code = funcEx.getCode();
            
            if (code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                return UploadErrorType.FUNCTION_TIMEOUT;
            }
            if (code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                return UploadErrorType.AUTH_ERROR;
            }
            if (code == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                return UploadErrorType.PERMISSION_DENIED;
            }
            if (code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                return UploadErrorType.QUOTA_EXCEEDED;
            }
            
            return UploadErrorType.FUNCTION_CALL_FAILED;
        }

        // Errores de Firestore (transacciones)
        if (exception instanceof FirebaseException) {
            String message = exception.getMessage();
            if (message != null && message.toLowerCase().contains("transaction")) {
                return UploadErrorType.FIRESTORE_TRANSACTION_FAILED;
            }
        }

        // Errores de validación
        if (exception instanceof IllegalArgumentException 
                || exception instanceof IllegalStateException) {
            String message = exception.getMessage();
            if (message != null) {
                if (message.toLowerCase().contains("file") || message.toLowerCase().contains("archivo")) {
                    return UploadErrorType.FILE_NOT_READABLE;
                }
                if (message.toLowerCase().contains("form") || message.toLowerCase().contains("input")) {
                    return UploadErrorType.INVALID_FORM_INPUT;
                }
            }
            return UploadErrorType.INVALID_FORM_INPUT;
        }

        // Default
        return UploadErrorType.UNKNOWN;
    }

    /**
     * Genera un mensaje detallado para logging (incluye stack trace si está en debug)
     * 
     * @param exception La excepción
     * @param isDebug Si la app está en modo debug
     * @return Mensaje detallado
     */
    @NonNull
    public static String getDetailedMessage(@Nullable Exception exception, boolean isDebug) {
        if (exception == null) {
            return "No exception details available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
        sb.append("Message: ").append(exception.getMessage()).append("\n");
        
        if (exception.getCause() != null) {
            sb.append("Cause: ").append(exception.getCause().getClass().getSimpleName()).append("\n");
            sb.append("Cause Message: ").append(exception.getCause().getMessage()).append("\n");
        }

        if (isDebug && exception.getStackTrace() != null && exception.getStackTrace().length > 0) {
            sb.append("\nStack trace (first 3 lines):\n");
            for (int i = 0; i < Math.min(3, exception.getStackTrace().length); i++) {
                sb.append("  at ").append(exception.getStackTrace()[i].toString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Determina si vale la pena loggear el error como ERROR o solo como WARNING
     */
    public static boolean isCritical(@NonNull UploadErrorType errorType) {
        switch (errorType) {
            case NETWORK_ERROR:
            case TIMEOUT:
            case FILE_NOT_READABLE:
            case INVALID_FILE_TYPE:
                return false; // Errores esperables, log como WARNING
            default:
                return true; // Errores inesperados, log como ERROR
        }
    }
}
