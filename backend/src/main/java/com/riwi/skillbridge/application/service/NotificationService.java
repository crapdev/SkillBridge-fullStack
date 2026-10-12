package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ManageMyNotificationsUseCase;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Notification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService implements ManageMyNotificationsUseCase {
    // Evita que un cliente pida miles de filas de una vez
    static final int MAX_LIMIT = 50;

    private final NotificationRepositoryPort notifications;
    private final UserAccountPort users;

    public NotificationService(NotificationRepositoryPort notifications, UserAccountPort users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Override
    public List<Notification> listMine(String email, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return notifications.findLatestByRecipient(userId(email), safeLimit);
    }

    @Override
    public long countUnread(String email) {
        return notifications.countUnread(userId(email));
    }

    @Override
    public void markAllRead(String email) {
        notifications.markAllRead(userId(email), Instant.now());
    }

    // El usuario sale siempre del token, nunca de un parámetro: nadie puede leer las notificaciones de otro
    private UUID userId(String email) {
        return users.findIdByEmail(email)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }
}
