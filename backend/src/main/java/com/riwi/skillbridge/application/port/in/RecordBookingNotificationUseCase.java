package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.NotificationType;

import java.util.UUID;

/** Genera las notificaciones de cliente y proveedor cuando ocurre algo con una reserva. */
public interface RecordBookingNotificationUseCase {
    void record(NotificationType type, UUID bookingId);
}
