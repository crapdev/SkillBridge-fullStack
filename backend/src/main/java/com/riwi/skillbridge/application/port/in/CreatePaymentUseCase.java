package com.riwi.skillbridge.application.port.in;

import java.util.UUID;

public interface CreatePaymentUseCase {
    String createPaymentIntent(UUID bookingId);
}
