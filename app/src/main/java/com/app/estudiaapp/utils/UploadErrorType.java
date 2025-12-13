package com.app.estudiaapp.utils;

/**
 * Tipos de error que pueden ocurrir durante el proceso de upload y generación de planes.
 * Cada tipo incluye un mensaje amigable para mostrar al usuario.
 */
public enum UploadErrorType {
    
    // Errores de archivo
    INVALID_FILE_TYPE("El archivo debe ser PDF", false),
    FILE_TOO_LARGE("El archivo excede 50 MB", false),
    FILE_NOT_READABLE("No se puede leer el archivo seleccionado", false),
    FILE_EMPTY("El archivo está vacío", false),
    
    // Errores de red y conexión
    NETWORK_ERROR("Error de conexión. Verifica tu internet e intenta de nuevo", true),
    TIMEOUT("La operación tardó demasiado. Intenta con un archivo más pequeño", true),
    
    // Errores de autenticación
    AUTH_ERROR("Error de autenticación. Inicia sesión nuevamente", false),
    PERMISSION_DENIED("No tienes permisos para realizar esta acción", false),
    
    // Errores de Firebase
    STORAGE_UPLOAD_FAILED("Error al subir el archivo a Storage", true),
    FIRESTORE_TRANSACTION_FAILED("Error al asignar ID del plan", true),
    FUNCTION_CALL_FAILED("Error al procesar el documento en el servidor", true),
    FUNCTION_TIMEOUT("El procesamiento está tardando más de lo esperado", true),
    
    // Errores de validación
    INVALID_FORM_INPUT("Verifica que todos los campos estén correctos", false),
    QUOTA_EXCEEDED("Has alcanzado el límite de planes. Actualiza tu membresía", false),
    
    // Errores generales
    UNKNOWN("Error desconocido. Intenta de nuevo", true);

    private final String userMessage;
    private final boolean retriable;

    UploadErrorType(String userMessage, boolean retriable) {
        this.userMessage = userMessage;
        this.retriable = retriable;
    }

    /**
     * Obtiene el mensaje amigable para mostrar al usuario
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Indica si el error es retriable (el usuario puede intentar de nuevo)
     */
    public boolean isRetriable() {
        return retriable;
    }

    /**
     * Obtiene el título del diálogo según el tipo de error
     */
    public String getDialogTitle() {
        if (this == NETWORK_ERROR || this == TIMEOUT) {
            return "Error de conexión";
        } else if (this == AUTH_ERROR || this == PERMISSION_DENIED) {
            return "Error de autenticación";
        } else if (this.name().startsWith("FILE_")) {
            return "Archivo no válido";
        } else if (this.name().contains("STORAGE") || this.name().contains("FIRESTORE") || this.name().contains("FUNCTION")) {
            return "Error al generar plan";
        }
        return "Error";
    }
}
