package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

public interface BookingEventPublisherPort {
    void bookingCreated(Booking booking);

    void bookingCancelled(Booking booking);

    void bookingPaid(Booking booking);

    void paymentFailed(Booking booking);

    /** BUG-05: la sesión ya ocurrió (notificaciones, n8n y lo que dependa de las reseñas). */
    void bookingCompleted(Booking booking);
}
