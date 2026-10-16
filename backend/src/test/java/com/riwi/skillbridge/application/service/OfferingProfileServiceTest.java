package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.OfferingDraft;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SCRUM-175 (HU-20): ficha completa de la mentoría en la capa de aplicación —
 * guardado del temario, duración, nivel y días, sus validaciones y la restricción de horarios por día.
 */
@ExtendWith(MockitoExtension.class)
class OfferingProfileServiceTest {

    private static final String EMAIL = "proveedor@email.com";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final List<SyllabusTopic> TWO_TOPICS =
            List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("Deployments", "Réplicas"));

    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private UserAccountPort users;
    @Mock private OfferingCachePort cache;

    @InjectMocks private ProviderOfferingService service;

    private final UUID providerId = UUID.randomUUID();

    @BeforeEach
    void provider() {
        lenient().when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(providerId));
        lenient().when(offerings.save(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(offerings.update(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static OfferingDraft draft(List<SyllabusTopic> syllabus, Integer duration, OfferingLevel level, Set<DayOfWeek> days) {
        return new OfferingDraft("Kubernetes", "Primeros pasos", "CLOUD", new BigDecimal("90000"), syllabus, duration, level, days);
    }

    private static OfferingDraft validDraft() {
        return draft(TWO_TOPICS, 90, OfferingLevel.INTERMEDIO, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
    }

    // ==========================================
    // GUARDAR LA FICHA (crear y editar)
    // ==========================================

    @Nested
    class SaveProfile {

        @Test
        void create_ShouldStoreSyllabusDurationLevelAndDays() {
            Offering created = service.createOffering(validDraft(), EMAIL);

            assertEquals(TWO_TOPICS, created.syllabus());
            assertEquals(90, created.durationMinutes());
            assertEquals(OfferingLevel.INTERMEDIO, created.level());
            assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), created.workingDays());
            assertEquals(providerId, created.providerId());
            verify(cache).evictActiveOfferings();
        }

        @Test
        void create_ShouldTrimTopicsKeepingTheirOrder() {
            OfferingDraft draft = draft(List.of(new SyllabusTopic("  Pods ", " Qué son  "), new SyllabusTopic("Helm", "Charts")),
                    60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.FRIDAY));

            Offering created = service.createOffering(draft, EMAIL);

            assertEquals(List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("Helm", "Charts")), created.syllabus());
        }

        @Test
        void update_ShouldReplaceProfileAndKeepOwnerAndStatus() {
            UUID id = UUID.randomUUID();
            Offering existing = new Offering(id, providerId, "Viejo", "Viejo", "CLOUD", BigDecimal.TEN, false,
                    List.of(new SyllabusTopic("A", "a"), new SyllabusTopic("B", "b")), 60, OfferingLevel.BASICO,
                    EnumSet.allOf(DayOfWeek.class));
            when(offerings.findById(id)).thenReturn(Optional.of(existing));

            service.updateOffering(id, validDraft(), EMAIL);

            ArgumentCaptor<Offering> sent = ArgumentCaptor.forClass(Offering.class);
            verify(offerings).update(sent.capture());
            assertEquals(providerId, sent.getValue().providerId());
            assertFalse(sent.getValue().active());
            assertEquals(TWO_TOPICS, sent.getValue().syllabus());
            assertEquals(90, sent.getValue().durationMinutes());
            assertEquals(OfferingLevel.INTERMEDIO, sent.getValue().level());
            assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), sent.getValue().workingDays());
            verify(cache).evictActiveOfferings();
        }
    }

    // ==========================================
    // VALIDACIONES DE LA FICHA (segunda barrera: 422)
    // ==========================================

    @Nested
    class Validations {

        private void assertRejected(OfferingDraft draft, String message) {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.createOffering(draft, EMAIL));
            assertEquals(message, ex.getMessage());
            verify(offerings, never()).save(any());
        }

        @Test
        void shouldRejectSyllabusWithLessThanTwoTopics() {
            assertRejected(draft(List.of(new SyllabusTopic("Pods", "Qué son")), 60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)),
                    "El temario debe tener al menos 2 temas");
            assertRejected(draft(List.of(), 60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)),
                    "El temario debe tener al menos 2 temas");
            assertRejected(draft(null, 60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)),
                    "El temario debe tener al menos 2 temas");
        }

        @Test
        void shouldRejectMoreThanTwelveTopics() {
            List<SyllabusTopic> thirteen = IntStream.rangeClosed(1, 13)
                    .mapToObj(i -> new SyllabusTopic("Tema " + i, "Descripción " + i)).toList();

            assertRejected(draft(thirteen, 60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)), "El temario admite máximo 12 temas");
        }

        @Test
        void shouldRejectTopicWithoutTitleOrDescription() {
            assertRejected(draft(List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("  ", "Réplicas")),
                    60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)), "Cada tema necesita título y descripción");
            assertRejected(draft(List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("Helm", null)),
                    60, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)), "Cada tema necesita título y descripción");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 15, 29, 241, 600})
        void shouldRejectDurationOutOfRange(int minutes) {
            assertRejected(draft(TWO_TOPICS, minutes, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)),
                    "La duración debe estar entre 30 y 240 minutos");
        }

        @Test
        void shouldRejectMissingDurationOrLevel() {
            assertRejected(draft(TWO_TOPICS, null, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)),
                    "La duración debe estar entre 30 y 240 minutos");
            assertRejected(draft(TWO_TOPICS, 60, null, Set.of(DayOfWeek.MONDAY)), "El nivel es obligatorio");
        }

        @Test
        void shouldRejectWhenNoWorkingDay() {
            assertRejected(draft(TWO_TOPICS, 60, OfferingLevel.BASICO, Collections.emptySet()), "Elige al menos un día de la semana");
            assertRejected(draft(TWO_TOPICS, 60, OfferingLevel.BASICO, null), "Elige al menos un día de la semana");
        }

        @Test
        void shouldAcceptDurationLimits() {
            assertDoesNotThrow(() -> service.createOffering(draft(TWO_TOPICS, 30, OfferingLevel.BASICO, Set.of(DayOfWeek.MONDAY)), EMAIL));
            assertDoesNotThrow(() -> service.createOffering(draft(TWO_TOPICS, 240, OfferingLevel.AVANZADO, Set.of(DayOfWeek.MONDAY)), EMAIL));
        }
    }

    // ==========================================
    // HORARIOS SOLO EN LOS DÍAS QUE SE OFRECE (SCRUM-172)
    // ==========================================

    @Nested
    class WorkingDaySlots {

        private final UUID offeringId = UUID.randomUUID();

        @BeforeEach
        void mondayAndWednesdayOffering() {
            Offering offering = new Offering(offeringId, providerId, "Kubernetes", "Pods", "CLOUD", BigDecimal.TEN, true,
                    TWO_TOPICS, 60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
            when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
            lenient().when(slots.save(any(AvailabilitySlot.class))).thenAnswer(i -> i.getArgument(0));
        }

        /** Próxima fecha (en Bogotá) que cae en ese día, a la hora indicada */
        private java.time.Instant next(DayOfWeek day, LocalTime time) {
            LocalDate date = LocalDate.now(BOGOTA).plusDays(1).with(TemporalAdjusters.nextOrSame(day));
            return date.atTime(time).atZone(BOGOTA).toInstant();
        }

        @Test
        void shouldPublishSlotOnAnOfferedDay() {
            AvailabilitySlot slot = service.addAvailabilitySlot(offeringId, next(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0)), EMAIL);

            assertEquals(offeringId, slot.offeringId());
            verify(slots).save(any(AvailabilitySlot.class));
        }

        @Test
        void shouldRejectSlotOnADayNotOffered() {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> service.addAvailabilitySlot(offeringId, next(DayOfWeek.SATURDAY, LocalTime.of(9, 0)), EMAIL));

            assertEquals("Esta mentoría solo se ofrece los días lunes y miércoles. El sábado no está entre ellos", ex.getMessage());
            verify(slots, never()).save(any());
        }

        @Test
        void shouldUseColombianTimeToDecideTheDay() {
            // Domingo 23:30 en Bogotá ya es lunes en UTC: debe rechazarse porque el proveedor lo publicó un domingo
            java.time.Instant sundayNight = next(DayOfWeek.SUNDAY, LocalTime.of(23, 30));
            assertEquals(DayOfWeek.MONDAY, sundayNight.atZone(ZoneId.of("UTC")).getDayOfWeek());

            assertThrows(BusinessRuleException.class, () -> service.addAvailabilitySlot(offeringId, sundayNight, EMAIL));

            // Lunes 00:30 en Bogotá sí es un día ofrecido
            assertDoesNotThrow(() -> service.addAvailabilitySlot(offeringId, next(DayOfWeek.MONDAY, LocalTime.of(0, 30)), EMAIL));
        }
    }
}
