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
import java.util.List;
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

    private BookingRepositoryPort bookings;
    private OfferingRepositoryPort offerings;
    private NotificationRepositoryPort notifications;
    private RecordBookingNotificationService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepositoryPort.class);
        offerings = mock(OfferingRepositoryPort.class);
        notifications = mock(NotificationRepositoryPort.class);
        service = new RecordBookingNotificationService(bookings, offerings, notifications);

        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(offeringId, providerId, "Java Básico", "Intro", "BACKEND", BigDecimal.TEN, true)));
        when(notifications.saveIfAbsent(any())).thenReturn(true);
    }

    @Test
    @DisplayName("Reserva creada: notifica al cliente y al proveedor")
    void bookingCreatedNotifiesCustomerAndProvider() {
        service.record(NotificationType.BOOKING_CREATED, bookingId);

        List<Notification> saved = captureSaved(2);
        assertThat(saved).extracting(Notification::recipientId).containsExactly(customerId, providerId);
        assertThat(saved).allMatch(n -> n.type() == NotificationType.BOOKING_CREATED && n.bookingId().equals(bookingId));
        assertThat(saved).allMatch(n -> !n.isRead());
    }

    @Test
    @DisplayName("El mensaje lleva el título y la fecha en hora de Colombia")
    void messageHasTitleAndColombianDate() {
        service.record(NotificationType.BOOKING_PAID, bookingId);

        List<Notification> saved = captureSaved(2);
        assertThat(saved.get(0).message())
                .isEqualTo("Tu pago fue aprobado: tu sesión de Java Básico del jueves 10 de octubre a las 10:00 está confirmada.");
        assertThat(saved.get(1).message()).isEqualTo("La reserva de Java Básico del jueves 10 de octubre a las 10:00 fue pagada.");
    }

    @Test
    @DisplayName("Pago fallido: solo notifica al cliente")
    void paymentFailedNotifiesOnlyTheCustomer() {
        service.record(NotificationType.PAYMENT_FAILED, bookingId);

        List<Notification> saved = captureSaved(1);
        assertThat(saved.get(0).recipientId()).isEqualTo(customerId);
        assertThat(saved.get(0).message()).contains("Java Básico").contains("Mis reservas");
    }

    @Test
    @DisplayName("Reserva cancelada: notifica al cliente y al proveedor")
    void bookingCancelledNotifiesBoth() {
        service.record(NotificationType.BOOKING_CANCELLED, bookingId);

        assertThat(captureSaved(2)).extracting(Notification::message)
                .containsExactly("Cancelaste tu reserva de Java Básico del jueves 10 de octubre a las 10:00.",
                        "Se canceló la reserva de Java Básico del jueves 10 de octubre a las 10:00.");
    }

    @Test
    @DisplayName("Una mentoría sin proveedor solo notifica al cliente")
    void offeringWithoutProviderNotifiesOnlyTheCustomer() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(offeringId, null, "Java Básico", "Intro", "BACKEND", BigDecimal.TEN, true)));

        service.record(NotificationType.BOOKING_CREATED, bookingId);

        assertThat(captureSaved(1).get(0).recipientId()).isEqualTo(customerId);
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

        verify(notifications, times(2)).saveIfAbsent(any());
    }

    private List<Notification> captureSaved(int expected) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(expected)).saveIfAbsent(captor.capture());
        return captor.getAllValues();
    }
}
