package com.riwi.skillbridge.domain.exception;

/**
 * El proveedor de IA no pudo generar la recomendación. El motivo permite responder con el
 * código HTTP adecuado (503 o 429) en lugar de tratar todos los fallos como una regla de negocio.
 */
public class AiProviderException extends RuntimeException {

    public enum Reason {
        /** API key inválida, sin permisos o servicio mal configurado: no se arregla reintentando */
        CONFIGURATION,
        /** Cuota agotada o demasiadas peticiones: conviene esperar antes de reintentar */
        RATE_LIMITED,
        /** Caída, timeout, error de red o respuesta vacía: se puede reintentar */
        UNAVAILABLE
    }

    private final Reason reason;

    public AiProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
