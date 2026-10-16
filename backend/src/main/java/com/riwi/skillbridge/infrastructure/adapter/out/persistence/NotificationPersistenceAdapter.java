package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.NotificationEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaNotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final JpaNotificationRepository repository;

    public NotificationPersistenceAdapter(JpaNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean saveIfAbsent(Notification notification) {
        if (repository.existsByBookingIdAndTypeAndRecipientId(
                notification.bookingId(), notification.type(), notification.recipientId())) {
            return false;
        }
        try {
            repository.saveAndFlush(toEntity(notification));
            return true;
        } catch (DataIntegrityViolationException duplicated) {
            // Dos entregas simultáneas del mismo evento pasaron el exists: la restricción UNIQUE dejó pasar solo una
            return false;
        }
    }

    @Override
    public List<Notification> findLatestByRecipient(UUID recipientId, int limit) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, Limit.of(limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countUnread(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    // Un UPDATE con @Modifying necesita una transacción abierta
    @Override
    @Transactional
    public void markAllRead(UUID recipientId, Instant readAt) {
        repository.markAllRead(recipientId, readAt);
    }

    private NotificationEntity toEntity(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notification.id());
        entity.setRecipientId(notification.recipientId());
        entity.setBookingId(notification.bookingId());
        entity.setType(notification.type());
        entity.setMessage(notification.message());
        entity.setCreatedAt(notification.createdAt());
        entity.setReadAt(notification.readAt());
        return entity;
    }

    private Notification toDomain(NotificationEntity entity) {
        return new Notification(entity.getId(), entity.getRecipientId(), entity.getBookingId(), entity.getType(),
            entity.getMessage(), entity.getCreatedAt(), entity.getReadAt());
    }
}
