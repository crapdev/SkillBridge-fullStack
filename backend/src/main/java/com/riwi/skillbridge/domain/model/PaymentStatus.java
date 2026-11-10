package com.riwi.skillbridge.domain.model;

public enum PaymentStatus {
    /** Cobro abierto: el cliente todavía puede pagarlo. */
    PENDING,
    PAID,
    /** La pasarela rechazó el pago (por ejemplo, tarjeta sin fondos). */
    FAILED,
    /**
     * BUG-09: el cobro se canceló porque ya no se iba a usar (se reemplazó por otro, cambió el precio o lo canceló
     * la pasarela). No es un rechazo: no cuenta en los reportes de pagos fallidos.
     */
    CANCELED
}
