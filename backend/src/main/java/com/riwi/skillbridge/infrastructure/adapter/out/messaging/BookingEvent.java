package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento del ciclo de vida de una reserva. El tipo (creada, cancelada, pagada, pago fallido) viaja en la
 * routing key. Solo lleva ids: quien lo consume busca lo que necesita y no circulan emails ni nombres.
 */
public record BookingEvent(
        UUID bookingId,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        Instant occurredAt
) {}
