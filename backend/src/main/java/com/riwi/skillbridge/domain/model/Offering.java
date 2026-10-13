package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mentoría publicada por un proveedor.
 * Desde HU-20 incluye su ficha: temario, duración de cada sesión, nivel y días de la semana en que se ofrece.
 */
public record Offering(
        UUID id,
        UUID providerId,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active,
        List<SyllabusTopic> syllabus,
        int durationMinutes,
        OfferingLevel level,
        Set<DayOfWeek> workingDays
) {
    /** Duración por defecto de una sesión cuando no se indica (la misma de la migración V8). */
    public static final int DEFAULT_DURATION_MINUTES = 60;

    public Offering {
        // Valores por defecto válidos: protegen datos viejos (p. ej. la caché) que aún no traen la ficha
        syllabus = syllabus == null ? List.of() : List.copyOf(syllabus);
        durationMinutes = durationMinutes > 0 ? durationMinutes : DEFAULT_DURATION_MINUTES;
        level = level == null ? OfferingLevel.BASICO : level;
        workingDays = orderedDays(workingDays);
    }

    /**
     * Mentoría sin ficha: temario vacío, 60 minutos, nivel básico y todos los días.
     * Se conserva para el código y las pruebas que solo necesitan los datos básicos.
     */
    public Offering(UUID id, UUID providerId, String title, String description, String category,
                    BigDecimal price, boolean active) {
        this(id, providerId, title, description, category, price, active,
                List.of(), DEFAULT_DURATION_MINUTES, OfferingLevel.BASICO, EnumSet.allOf(DayOfWeek.class));
    }

    /** ¿El proveedor ofrece esta mentoría ese día de la semana? */
    public boolean offersOn(DayOfWeek day) {
        return workingDays.contains(day);
    }

    // Lunes a domingo en orden, sin duplicados e inmodificable. Sin dato (null) = todos los días, como en la migración
    private static Set<DayOfWeek> orderedDays(Collection<DayOfWeek> days) {
        if (days == null) return Collections.unmodifiableSet(EnumSet.allOf(DayOfWeek.class));
        if (days.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(days));
    }
}
