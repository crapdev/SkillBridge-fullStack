package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Varios horarios a publicar de una vez; el límite evita lotes desproporcionados */
public record CreateSlotsRequest(@NotEmpty @Size(max = 500) List<@NotNull Instant> scheduledAt) {}
