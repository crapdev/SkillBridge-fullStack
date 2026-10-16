package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Notification(
    UUID id,
    UUID recipientId,
    UUID bookingId,
    NotificationType type,
    String message,
    Instant createdAt,
    Instant readAt
) {
    public boolean isRead() {
        return readAt != null;
    }
}
