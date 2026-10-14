package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decisión del administrador sobre una mentoría pendiente (HU-21). El motivo es opcional y solo aplica al rechazo. */
public record AdminOfferingReviewRequest(
        @NotNull(message = "La decisión es obligatoria") OfferingReviewStatus decision,
        @Size(max = 300, message = "El motivo admite como máximo 300 caracteres") String reason) {}
