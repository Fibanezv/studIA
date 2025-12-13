package com.app.estudiaapp.utils;

/**
 * Tipos de error generales que pueden ocurrir en operaciones de Auth, Firestore y red.
 * Se usa como clasificación estándar en toda la app para mostrar mensajes consistentes.
 */
public enum GeneralErrorType {
    
    // Errores de red
    NETWORK_ERROR("Error de conexión", "Error de Conexión", "No pudimos conectar con el servidor. Verifica tu conexión a internet e intenta de nuevo.", true),
    TIMEOUT("Tiempo agotado", "Operación Tardada", "La operación está tardando demasiado. Verifica tu conexión e intenta de nuevo.", true),
    
    // Errores de autenticación
    USER_COLLISION("Usuario existente", "Correo Registrado", "Este correo ya está registrado. Intenta con otro o usa 'Iniciar Sesión'.", false),
    WEAK_PASSWORD("Contraseña débil", "Contraseña Insegura", "Tu contraseña es muy débil. Usa al menos 6 caracteres con letras y números.", false),
    INVALID_CREDENTIALS("Credenciales inválidas", "Error de Acceso", "El correo o la contraseña son incorrectos. Intenta de nuevo.", false),
    INVALID_EMAIL("Email inválido", "Email Incorrecto", "El formato del email no es válido. Verifica e intenta de nuevo.", false),
    AUTH_ERROR("Error de autenticación", "Error de Autenticación", "Algo salió mal con tu autenticación. Intenta de nuevo o inicia sesión nuevamente.", false),
    
    // Errores de Firebase/Firestore
    FIRESTORE_READ_FAILED("Error al leer datos", "Lectura Fallida", "No pudimos obtener los datos del servidor. Intenta de nuevo.", true),
    FIRESTORE_WRITE_FAILED("Error al guardar datos", "Guardado Fallido", "No pudimos guardar los cambios. Intenta de nuevo.", true),
    PERMISSION_DENIED("Acceso denegado", "Permisos Insuficientes", "No tienes permisos para realizar esta acción.", false),
    
    // Errores generales
    UNKNOWN("Error desconocido", "Algo Salió Mal", "Ocurrió un error inesperado. Intenta de nuevo o contacta con soporte.", true);

    private final String shortTitle;
    private final String dialogTitle;
    private final String userMessage;
    private final boolean retriable;

    GeneralErrorType(String shortTitle, String dialogTitle, String userMessage, boolean retriable) {
        this.shortTitle = shortTitle;
        this.dialogTitle = dialogTitle;
        this.userMessage = userMessage;
        this.retriable = retriable;
    }

    /**
     * Obtiene el título corto del error
     */
    public String getShortTitle() {
        return shortTitle;
    }

    /**
     * Obtiene el título del diálogo para mostrar al usuario
     */
    public String getDialogTitle() {
        return dialogTitle;
    }

    /**
     * Obtiene el mensaje amigable para mostrar al usuario
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Indica si el error es reintentable (ej: network, timeout)
     */
    public boolean isRetriable() {
        return retriable;
    }
}
