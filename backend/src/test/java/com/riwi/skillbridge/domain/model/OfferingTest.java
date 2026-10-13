package com.riwi.skillbridge.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-175 (HU-20): reglas del modelo de dominio de la ficha de mentoría. */
class OfferingTest {

    private static Offering withProfile(List<SyllabusTopic> syllabus, int duration, OfferingLevel level, Set<DayOfWeek> days) {
        return new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true,
                syllabus, duration, level, days);
    }

    @Test
    void basicConstructor_ShouldUseValidDefaults() {
        Offering offering = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);

        assertTrue(offering.syllabus().isEmpty());
        assertEquals(60, offering.durationMinutes());
        assertEquals(OfferingLevel.BASICO, offering.level());
        assertEquals(EnumSet.allOf(DayOfWeek.class), offering.workingDays());
    }

    @Test
    void missingValues_ShouldFallBackToDefaults() {
        // Así llegan las mentorías guardadas en caché antes de HU-20
        Offering offering = withProfile(null, 0, null, null);

        assertEquals(List.of(), offering.syllabus());
        assertEquals(Offering.DEFAULT_DURATION_MINUTES, offering.durationMinutes());
        assertEquals(OfferingLevel.BASICO, offering.level());
        assertEquals(7, offering.workingDays().size());
    }

    @Test
    void workingDays_ShouldBeOrderedFromMondayToSunday() {
        Offering offering = withProfile(List.of(), 60, OfferingLevel.AVANZADO,
                Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), List.copyOf(offering.workingDays()));
    }

    @Test
    void offersOn_ShouldOnlyAcceptConfiguredDays() {
        Offering offering = withProfile(List.of(), 60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.TUESDAY));

        assertTrue(offering.offersOn(DayOfWeek.TUESDAY));
        assertFalse(offering.offersOn(DayOfWeek.SUNDAY));
    }

    @Test
    void profile_ShouldBeImmutable() {
        List<SyllabusTopic> topics = new ArrayList<>(List.of(new SyllabusTopic("A", "a"), new SyllabusTopic("B", "b")));
        Offering offering = withProfile(topics, 60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.MONDAY));

        topics.add(new SyllabusTopic("C", "c"));

        assertEquals(2, offering.syllabus().size());
        assertThrows(UnsupportedOperationException.class, () -> offering.syllabus().add(new SyllabusTopic("D", "d")));
        assertThrows(UnsupportedOperationException.class, () -> offering.workingDays().add(DayOfWeek.SUNDAY));
    }
}
