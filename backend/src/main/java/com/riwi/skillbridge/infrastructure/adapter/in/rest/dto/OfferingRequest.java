package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.application.port.in.OfferingDraft;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * Cuerpo de POST y PUT /api/provider/offerings: datos básicos (HU-19) más la ficha completa (HU-20).
 * Si una regla falla, el backend responde 400 con el campo y el motivo en "detail".
 */
public record OfferingRequest(
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
        BigDecimal price,

        @NotNull(message = "El temario es obligatorio")
        @Size(min = OfferingDraft.MIN_TOPICS, message = "El temario debe tener al menos 2 temas")
        @Size(max = OfferingDraft.MAX_TOPICS, message = "El temario admite máximo 12 temas")
        List<@NotNull(message = "Cada tema necesita título y descripción") @Valid Topic> syllabus,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = OfferingDraft.MIN_DURATION_MINUTES, message = "La duración mínima es de 30 minutos")
        @Max(value = OfferingDraft.MAX_DURATION_MINUTES, message = "La duración máxima es de 240 minutos")
        Integer durationMinutes,

        @NotNull(message = "El nivel es obligatorio")
        OfferingLevel level,

        @NotEmpty(message = "Elige al menos un día de la semana")
        Set<@NotNull(message = "Día de la semana no válido") DayOfWeek> workingDays
) {
    /** Un tema del temario: título corto y descripción breve. */
    public record Topic(
            @NotBlank(message = "Cada tema necesita un título")
            @Size(max = 120, message = "El título del tema no puede superar 120 caracteres")
            String title,

            @NotBlank(message = "Cada tema necesita una descripción breve")
            @Size(max = 300, message = "La descripción del tema no puede superar 300 caracteres")
            String description
    ) {}

    public OfferingDraft toDraft() {
        return new OfferingDraft(
                title,
                description,
                category,
                price,
                syllabus.stream().map(t -> new SyllabusTopic(t.title(), t.description())).toList(),
                durationMinutes,
                level,
                workingDays
        );
    }
}
