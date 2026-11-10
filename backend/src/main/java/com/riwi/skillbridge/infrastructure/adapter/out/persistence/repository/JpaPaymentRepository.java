package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    // Método clave para el Webhook de Stripe
    Optional<PaymentEntity> findByStripePaymentIntentId(String intentId);

    // BUG-09: el índice uq_payments_active_booking garantiza que hay como máximo uno
    Optional<PaymentEntity> findFirstByBooking_IdAndStatusIn(UUID bookingId, Collection<PaymentStatus> statuses);
}
