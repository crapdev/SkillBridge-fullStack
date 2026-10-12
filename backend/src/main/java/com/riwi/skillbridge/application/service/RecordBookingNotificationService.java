package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.RecordBookingNotificationUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.domain.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisa al proveedor de lo que pasa con las reservas de sus mentorías. El cliente no recibe
 * notificaciones: cada acción suya (reservar, cancelar, pagar) ya muestra el resultado en pantalla.
 */
@Service
public class RecordBookingNotificationService implements RecordBookingNotificationUseCase {
    private static final Logger log = LoggerFactory.getLogger(RecordBookingNotificationService.class);

    // Las sesiones se agendan en hora de Colombia, igual que las muestra el frontend. Formato de 24 h:
    // "a. m." usa espacios invisibles (U+202F) y su punto final se duplicaría al terminar la frase
    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter
        .ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-CO"))
        .withZone(ZoneId.of("America/Bogota"));

    private final BookingRepositoryPort bookings;
    private final OfferingRepositoryPort offerings;
    private final NotificationRepositoryPort notifications;

    public RecordBookingNotificationService(BookingRepositoryPort bookings, OfferingRepositoryPort offerings,
                                            NotificationRepositoryPort notifications) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.notifications = notifications;
    }

    @Override
    public void record(NotificationType type, UUID bookingId) {
        Optional<Booking> booking = bookings.findById(bookingId);
        if (booking.isEmpty()) {
            // Reintentar no la va a crear: se registra y se descarta en lugar de llenar la DLQ
            log.warn("Notificación descartada: la reserva no existe (tipo={}, bookingId={})", type, bookingId);
            return;
        }
        Booking b = booking.get();
        Optional<Offering> offering = offerings.findById(b.offeringId());
        UUID providerId = offering.map(Offering::providerId).orElse(null);
        if (providerId == null) {
            log.debug("Notificación omitida: la mentoría no tiene proveedor (tipo={}, bookingId={})", type, bookingId);
            return;
        }

        String message = message(type, offering.get().title(), SESSION_DATE.format(b.scheduledAt()));
        boolean saved = notifications.saveIfAbsent(
            new Notification(UUID.randomUUID(), providerId, b.id(), type, message, Instant.now(), null));
        if (!saved) {
            log.debug("Notificación repetida ignorada (tipo={}, bookingId={})", type, bookingId);
        }
    }

    private static String message(NotificationType type, String title, String date) {
        return switch (type) {
            case BOOKING_CREATED -> "Nueva reserva de %s para el %s.".formatted(title, date);
            case BOOKING_CANCELLED -> "Se canceló la reserva de %s del %s.".formatted(title, date);
            case BOOKING_PAID -> "La reserva de %s del %s fue pagada.".formatted(title, date);
        };
    }
}
