package com.riwi.skillbridge.domain.exception;

/**
 * BUG-09: la reserva ya tiene un pago activo (PENDING o PAID). La base de datos solo admite uno por reserva
 * (índice {@code uq_payments_active_booking}); esto pasa cuando dos peticiones abren el pago al mismo tiempo.
 */
public class ActivePaymentAlreadyExistsException extends RuntimeException {
    public ActivePaymentAlreadyExistsException() {
        super("Esta reserva ya tiene un pago en curso");
    }
}
