package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;
import java.util.UUID;

public interface CancelBookingUseCase {
    Booking cancelBooking(UUID bookingId, String customerEmail);
}
