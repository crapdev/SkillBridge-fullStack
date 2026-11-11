package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param price BUG-10: precio de la mentoría en el momento de reservar. Es lo que se cobra, aunque el proveedor
 *              cambie después el precio de la mentoría.
 */
public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status,
        BigDecimal price) {

    public Booking {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("La reserva debe guardar un precio válido");
        }
    }

    // HU-09: Lógica de cancelación centralizada en el dominio
    public Booking cancel(Instant now) {
        if (this.status == BookingStatus.CANCELLED) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "La reserva ya se encuentra cancelada");
        }
        if (this.status == BookingStatus.COMPLETED) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "No se puede cancelar una reserva completada");
        }

        // Regla: 24 horas de antelación mínimo
        java.time.Instant deadline = this.scheduledAt.minus(24, java.time.temporal.ChronoUnit.HOURS);
        if (now.isAfter(deadline)) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "La reserva solo puede ser cancelada con al menos 24 horas de antelación");
        }

        // Retornamos una copia inmutable con el nuevo estado para respetar BUG-01 en
        // persistencia
        return new Booking(this.id, this.offeringId, this.customerId, this.scheduledAt, BookingStatus.CANCELLED, this.price);
    }

    // HU-15: solo se cobran las reservas que siguen pendientes
    public boolean isPayable() {
        return this.status == BookingStatus.CREATED;
    }

    // HU-15: el pago aprobado confirma la reserva
    public Booking confirm() {
        if (!isPayable()) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "Solo se pueden confirmar reservas pendientes");
        }
        return new Booking(this.id, this.offeringId, this.customerId, this.scheduledAt, BookingStatus.CONFIRMED, this.price);
    }

    // BUG-05: la sesión termina a la hora agendada más la duración de la mentoría
    public Instant endsAt(int durationMinutes) {
        return this.scheduledAt.plus(durationMinutes, java.time.temporal.ChronoUnit.MINUTES);
    }

    // BUG-05: solo una reserva confirmada (pagada) cuya sesión ya terminó puede completarse
    public boolean isFinished(Instant now, int durationMinutes) {
        return this.status == BookingStatus.CONFIRMED && !now.isBefore(endsAt(durationMinutes));
    }

    // BUG-05: la sesión ya ocurrió; desde aquí se puede calificar (HU-27)
    public Booking complete(Instant now, int durationMinutes) {
        if (this.status != BookingStatus.CONFIRMED) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "Solo se pueden completar reservas confirmadas");
        }
        if (now.isBefore(endsAt(durationMinutes))) {
            throw new com.riwi.skillbridge.domain.exception.BusinessRuleException(
                    "La sesión todavía no ha terminado");
        }
        return new Booking(this.id, this.offeringId, this.customerId, this.scheduledAt, BookingStatus.COMPLETED, this.price);
    }
}
