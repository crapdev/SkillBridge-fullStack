package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.domain.model.NotificationType;

import java.time.Instant;
import java.util.UUID;

// No expone recipientId: el usuario ya sabe que son suyas
public record NotificationResponse(UUID id, UUID bookingId, NotificationType type, String message,
                                   Instant createdAt, boolean read) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.id(), notification.bookingId(), notification.type(),
            notification.message(), notification.createdAt(), notification.isRead());
    }
}
