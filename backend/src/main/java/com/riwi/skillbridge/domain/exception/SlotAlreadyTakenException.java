package com.riwi.skillbridge.domain.exception;

/**
 * BUG-08: otra reserva ganó la franja entre la revisión y el guardado (dos clientes reservaron al mismo tiempo).
 * La API responde 409 Conflict: el cliente puede elegir otro horario.
 */
public class SlotAlreadyTakenException extends RuntimeException {
    public SlotAlreadyTakenException() {
        super("Este horario acaba de ser apartado por otro usuario. Elige otro horario.");
    }
}
