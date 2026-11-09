package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecordBookingNotificationServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    // 2030-10-10 15:00 UTC = jueves 10 de octubre a las 10:00 en Colombia
    private final Booking booking = new Booking(bookingId, offeringId, customerId,
            Instant.parse("2030-10-10T15:00:00Z"), BookingStatus.CREATED);
    // "Ahora" fijo: 19/10/2026 23:30 en Colombia
    private static final Instant NOW = Instant.parse("2026-10-20T04:30:00Z");
    private final BusinessTime businessTime =
        new BusinessTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Bogota"));
    private BookingRepositoryPort bookings;
    private OfferingRepositoryPort offerings;
    private NotificationRepositoryPort notifications;
    private RecordBookingNotificationService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepositoryPort.class);
        offerings = mock(OfferingRepositoryPort.class);
        notifications = mock(NotificationRepositoryPort.class);
        service = new RecordBookingNotificationService(bookings, offerings, notifications, businessTime);

        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(offeringId, providerId, "Java Básico", "Intro", "BACKEND", BigDecimal.TEN, true)));
        when(notifications.saveIfAbsent(any())).thenReturn(true);
    }

    @Test
    @DisplayName("Solo el proveedor recibe la notificación, nunca el cliente")
    void onlyTheProviderIsNotified() {
        service.record(NotificationType.BOOKING_CREATED, bookingId);

        Notification saved = captureSaved();
        assertThat(saved.recipientId()).isEqualTo(providerId).isNotEqualTo(customerId);
        assertThat(saved.bookingId()).isEqualTo(bookingId);
        assertThat(saved.type()).isEqualTo(NotificationType.BOOKING_CREATED);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("Reserva creada: mensaje con el título y la fecha en hora de Colombia")
    void bookingCreatedMessage() {
        service.record(NotificationType.BOOKING_CREATED, bookingId);

        assertThat(captureSaved().message()).isEqualTo("Nueva reserva de Java Básico para el jueves 10 de octubre a las 10:00.");
    }

    @Test
    @DisplayName("Reserva cancelada: mensaje para el proveedor")
    void bookingCancelledMessage() {
        service.record(NotificationType.BOOKING_CANCELLED, bookingId);

        assertThat(captureSaved().message()).isEqualTo("Se canceló la reserva de Java Básico del jueves 10 de octubre a las 10:00.");
    }

    @Test
    @DisplayName("Reserva pagada: mensaje para el proveedor")
    void bookingPaidMessage() {
        service.record(NotificationType.BOOKING_PAID, bookingId);

        assertThat(captureSaved().message()).isEqualTo("La reserva de Java Básico del jueves 10 de octubre a las 10:00 fue pagada.");
    }

    @Test
    @DisplayName("Una mentoría sin proveedor no genera notificación")
    void offeringWithoutProviderIsSkipped() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(offeringId, null, "Java Básico", "Intro", "BACKEND", BigDecimal.TEN, true)));

        service.record(NotificationType.BOOKING_CREATED, bookingId);

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("Si la reserva no existe se descarta sin error")
    void missingBookingIsDiscarded() {
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        service.record(NotificationType.BOOKING_CREATED, bookingId);

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("Un evento repetido no falla aunque no se guarde")
    void repeatedEventDoesNotFail() {
        when(notifications.saveIfAbsent(any())).thenReturn(false);

        service.record(NotificationType.BOOKING_CREATED, bookingId);

        verify(notifications).saveIfAbsent(any());
    }

    @Test
    @DisplayName("La notificación se fecha con el reloj inyectado, no con la hora real")
    void notificationUsesInjectedClock() {
        service.record(NotificationType.BOOKING_CREATED, bookingId);

        assertThat(captureSaved().createdAt()).isEqualTo(NOW);
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).saveIfAbsent(captor.capture());
        return captor.getValue();
    }
}
