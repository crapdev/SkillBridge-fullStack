package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import java.util.UUID;

// Angular nos enviará el ID de la reserva que el usuario quiere pagar
public record CreatePaymentRequest(
    UUID bookingId
) {}
