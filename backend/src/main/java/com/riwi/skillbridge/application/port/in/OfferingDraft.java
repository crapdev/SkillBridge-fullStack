package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.SyllabusTopic;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * Datos que el proveedor envía al crear o editar una mentoría (HU-19 y HU-20).
 * El dueño, el id y el estado no vienen aquí: los decide el caso de uso.
 */
public record OfferingDraft(
        String title,
        String description,
        String category,
        BigDecimal price,
        List<SyllabusTopic> syllabus,
        Integer durationMinutes,
        OfferingLevel level,
        Set<DayOfWeek> workingDays
) {
    /** Reglas de la ficha compartidas por Bean Validation (400) y el servicio (422). */
    public static final int MIN_TOPICS = 2;
    public static final int MAX_TOPICS = 12;
    public static final int MIN_DURATION_MINUTES = 30;
    public static final int MAX_DURATION_MINUTES = 240;
}
