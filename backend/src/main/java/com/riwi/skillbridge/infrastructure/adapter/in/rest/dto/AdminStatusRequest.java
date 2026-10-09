package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AdminStatusRequest(@NotNull(message = "El estado es obligatorio") AccountStatus status) {}
