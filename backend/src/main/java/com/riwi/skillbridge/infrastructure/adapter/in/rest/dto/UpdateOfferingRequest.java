package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos editables de una mentoría (HU-19). El proveedor dueño y el estado activo no se envían:
 * se conservan los que ya tiene la mentoría.
 */
public record UpdateOfferingRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 160, message = "El título no puede superar 160 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 1200, message = "La descripción no puede superar 1200 caracteres")
        String description,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 80, message = "La categoría no puede superar 80 caracteres")
        String category,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El precio debe ser mayor a cero")
        BigDecimal price
) {}
