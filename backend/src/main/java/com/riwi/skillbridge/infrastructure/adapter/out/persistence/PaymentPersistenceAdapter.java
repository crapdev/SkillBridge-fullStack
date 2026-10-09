package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaPaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentPersistenceAdapter implements PaymentPort {

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

        // Como la reserva es llave foránea obligatoria, debemos buscarla y asignarla
        BookingEntity bookingEntity = bookingRepository.findById(payment.bookingId())
            .orElseThrow(() -> new IllegalArgumentException("Booking no encontrada con ID: " + payment.bookingId()));
        entity.setBooking(bookingEntity);

        // 2. Guardar en la base de datos
        PaymentEntity savedEntity = paymentRepository.save(entity);

        // 3. Mapear de Entidad JPA de vuelta a Dominio
        return mapToDomain(savedEntity);
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
