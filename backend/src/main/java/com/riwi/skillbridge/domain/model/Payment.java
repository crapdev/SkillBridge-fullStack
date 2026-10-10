package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
    UUID id,
    UUID bookingId,
    BigDecimal amount,
    String currency,
    String stripePaymentIntentId,
    PaymentStatus status,
    Instant createdAt
) {}
