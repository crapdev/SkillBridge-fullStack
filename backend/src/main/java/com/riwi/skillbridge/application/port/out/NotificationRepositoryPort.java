package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryPort {
    /** Guarda la notificación si no existe otra igual (reserva + tipo + destinatario). Devuelve true si la guardó. */
    boolean saveIfAbsent(Notification notification);

    List<Notification> findLatestByRecipient(UUID recipientId, int limit);

    long countUnread(UUID recipientId);

    void markAllRead(UUID recipientId, Instant readAt);
}
