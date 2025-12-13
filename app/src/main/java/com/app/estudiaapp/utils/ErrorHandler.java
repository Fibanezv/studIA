package com.app.estudiaapp.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.StorageException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Helper para clasificar excepciones generales de Auth, Firestore y red.
 * Proporciona un mecanismo consistente para determinar qué tipo de error ocurrió
 * y cómo manejarlo (ej: si es reintentable).
 */
public class ErrorHandler {

    private static final String TAG = "ErrorHandler";

    /**
     * Clasifica una excepción en un GeneralErrorType
     * 
     * @param exception La excepción a clasificar
     * @return El tipo de error correspondiente
     */
    @NonNull
    public static GeneralErrorType classify(@Nullable Exception exception) {
        return classify(exception, null);
    }

    /**
     * Clasifica una excepción en un GeneralErrorType con contexto adicional
     * 
     * @param exception La excepción a clasificar
     * @param context Descripción del contexto donde ocurrió (ej: "MainActivity.loadPlans")
     * @return El tipo de error correspondiente
     */
    @NonNull
    public static GeneralErrorType classify(@Nullable Exception exception, @Nullable String context) {
        if (exception == null) {
            return GeneralErrorType.UNKNOWN;
        }

        String contextStr = context != null ? " [" + context + "]" : "";
        Log.w(TAG, "Classifying exception: " + exception.getClass().getSimpleName() + " - " + exception.getMessage() + contextStr);

        // Errores de red
        if (exception instanceof FirebaseNetworkException 
                || exception instanceof IOException 
                || exception.getCause() instanceof IOException) {
            return GeneralErrorType.NETWORK_ERROR;
        }

        // Timeouts
        if (exception instanceof TimeoutException 
                || exception instanceof SocketTimeoutException
                || exception.getCause() instanceof TimeoutException) {
            return GeneralErrorType.TIMEOUT;
        }

        // Errores de autenticación Firebase
        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authEx = (FirebaseAuthException) exception;
            
            // User collision (email already registered)
            if (exception instanceof FirebaseAuthUserCollisionException) {
                return GeneralErrorType.USER_COLLISION;
            }
            
            // Weak password
            if (exception instanceof FirebaseAuthWeakPasswordException) {
                return GeneralErrorType.WEAK_PASSWORD;
            }
            
            // Invalid credentials
            if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                return GeneralErrorType.INVALID_CREDENTIALS;
            }
            
            String errorCode = authEx.getErrorCode();
            
            if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
                return GeneralErrorType.INVALID_EMAIL;
            }
            
            if ("ERROR_PERMISSION_DENIED".equals(errorCode)) {
                return GeneralErrorType.PERMISSION_DENIED;
            }
            
            return GeneralErrorType.AUTH_ERROR;
        }

        // Errores de Firestore
        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) exception;
            
            switch (firestoreEx.getCode()) {
                case PERMISSION_DENIED:
                    return GeneralErrorType.PERMISSION_DENIED;
                case UNAVAILABLE:
                case UNAUTHENTICATED:
                case INTERNAL:
                case DATA_LOSS:
                case UNKNOWN:
                    return GeneralErrorType.FIRESTORE_WRITE_FAILED;
                case NOT_FOUND:
                case INVALID_ARGUMENT:
                case ALREADY_EXISTS:
                case FAILED_PRECONDITION:
                case OUT_OF_RANGE:
                case UNIMPLEMENTED:
                case DEADLINE_EXCEEDED:
                default:
                    return GeneralErrorType.FIRESTORE_READ_FAILED;
            }
        }

        // Errores de Storage
        if (exception instanceof StorageException) {
            StorageException storageEx = (StorageException) exception;
            int errorCode = storageEx.getErrorCode();
            
            if (errorCode == StorageException.ERROR_NOT_AUTHENTICATED) {
                return GeneralErrorType.AUTH_ERROR;
            }
            
            if (errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                return GeneralErrorType.PERMISSION_DENIED;
            }
            
            if (errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED
                    || errorCode == StorageException.ERROR_BUCKET_NOT_FOUND) {
                return GeneralErrorType.NETWORK_ERROR;
            }
        }

        // Error genérico
        return GeneralErrorType.UNKNOWN;
    }

    /**
     * Determina si un error es reintentable basándose en su tipo
     */
    public static boolean isRetriable(@Nullable Exception exception) {
        GeneralErrorType errorType = classify(exception);
        return errorType.isRetriable();
    }

    /**
     * Log estructurado con contexto
     */
    public static void logError(@Nullable Exception exception, @NonNull String context) {
        GeneralErrorType errorType = classify(exception, context);
        Log.e(TAG, "Error in " + context + ": " + errorType.name() + " - " + errorType.getUserMessage(), exception);
    }
}
