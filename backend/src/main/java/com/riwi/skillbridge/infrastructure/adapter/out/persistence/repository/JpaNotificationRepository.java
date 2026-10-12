package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Limit limit);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByBookingIdAndTypeAndRecipientId(UUID bookingId, NotificationType type, UUID recipientId);

    // Un solo UPDATE en lugar de cargar y guardar cada notificación. El UPDATE masivo no pasa por la caché
    // de Hibernate: clearAutomatically la vacía para que una lectura posterior no devuelva entidades viejas
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
