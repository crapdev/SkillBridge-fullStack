package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * HU-27: promedio y cantidad de calificaciones (de una mentoría o de un mentor).
 * Sin calificaciones el promedio es null: "sin calificar" no es lo mismo que "0 estrellas".
 */
public record RatingSummary(Double average, long count) {

    public static final RatingSummary EMPTY = new RatingSummary(null, 0);

    public RatingSummary {
        if (count <= 0) {
            average = null;
            count = 0;
        } else if (average != null) {
            // Un decimal, como se muestra en pantalla (4.666 -> 4.7)
            average = BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
    }
}
