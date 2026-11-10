package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.exception.ActivePaymentAlreadyExistsException;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaPaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentPort {

    /** BUG-09: índice único parcial de V11 (un solo pago PENDING o PAID por reserva). */
    static final String ACTIVE_PAYMENT_CONSTRAINT = "uq_payments_active_booking";
    /** Estados que cuentan para ese índice; deben coincidir con el WHERE de V11. */
    private static final Set<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PAID);

    private final JpaPaymentRepository paymentRepository;
    private final JpaBookingRepository bookingRepository;

    public PaymentPersistenceAdapter(JpaPaymentRepository paymentRepository, JpaBookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Payment save(Payment payment) {
        // 1. Mapear de Dominio a Entidad JPA
        PaymentEntity entity = new PaymentEntity();

        // Si el pago ya tiene ID, es una actualización
        if (payment.id() != null) {
            entity = paymentRepository.findById(payment.id())
                .orElse(new PaymentEntity());
        }

        entity.setAmount(payment.amount());
        entity.setCurrency(payment.currency());
        entity.setStatus(payment.status());
        entity.setStripePaymentIntentId(payment.stripePaymentIntentId());

        // La fecha la decide el servicio (Clock); en una actualización se conserva la original
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(payment.createdAt());
        }

        // Como la reserva es llave foránea obligatoria, debemos buscarla y asignarla
        BookingEntity bookingEntity = bookingRepository.findById(payment.bookingId())
            .orElseThrow(() -> new IllegalArgumentException("Booking no encontrada con ID: " + payment.bookingId()));
        entity.setBooking(bookingEntity);

        // 2. Guardar en la base de datos. Flush inmediato para que el índice único falle aquí y no al confirmar
        PaymentEntity savedEntity;
        try {
            savedEntity = paymentRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains(ACTIVE_PAYMENT_CONSTRAINT)) {
                throw new ActivePaymentAlreadyExistsException();
            }
            throw e;
        }

        // 3. Mapear de Entidad JPA de vuelta a Dominio
        return mapToDomain(savedEntity);
    }

    @Override
    public Optional<Payment> findActiveByBookingId(UUID bookingId) {
        return paymentRepository.findFirstByBooking_IdAndStatusIn(bookingId, ACTIVE_STATUSES)
            .map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByStripeIntentId(String intentId) {
        return paymentRepository.findByStripePaymentIntentId(intentId)
            .map(this::mapToDomain);
    }

    // Método auxiliar para traducir de Entity a Record de Dominio
    private Payment mapToDomain(PaymentEntity entity) {
        return new Payment(
            entity.getId(),
            entity.getBooking().getId(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStripePaymentIntentId(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
