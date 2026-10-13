package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Un mentor no puede tener dos sesiones que se crucen: cada horario ocupa su inicio + la duración de la mentoría. */
@ExtendWith(MockitoExtension.class)
class SlotOverlapServiceTest {

    private static final String OWNER = "laura@test.com";

    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private UserAccountPort users;
    @Mock private OfferingCachePort cache;

    @InjectMocks private ProviderOfferingService service;

    private final UUID providerId = UUID.randomUUID();
    // Mentoría de 90 minutos (como la del caso reportado) y otra del mismo proveedor de 60
    private final Offering java = offering("Java", 90);
    private final Offering angular = offering("Angular", 60);

    private Offering offering(String title, int minutes) {
        return new Offering(UUID.randomUUID(), providerId, title, "Mentoría", "BACKEND", BigDecimal.TEN, true,
                List.of(), minutes, OfferingLevel.BASICO, EnumSet.allOf(DayOfWeek.class));
    }

    /** Pasado mañana a la hora indicada, en la hora de Colombia */
    private static Instant at(int hour, int minute) {
        return LocalDate.now(ProviderOfferingService.BUSINESS_ZONE).plusDays(2)
                .atTime(LocalTime.of(hour, minute)).atZone(ProviderOfferingService.BUSINESS_ZONE).toInstant();
    }

    private AvailabilitySlot slot(Offering offering, Instant start) {
        return new AvailabilitySlot(UUID.randomUUID(), offering.id(), start, false);
    }

    @BeforeEach
    void setUp() {
        lenient().when(users.findIdByEmail(OWNER)).thenReturn(Optional.of(providerId));
        lenient().when(offerings.findById(java.id())).thenReturn(Optional.of(java));
        lenient().when(offerings.findByProviderId(providerId)).thenReturn(List.of(java, angular));
        lenient().when(slots.findByOfferingId(angular.id())).thenReturn(List.of());
        lenient().when(slots.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("13:00 ocupa hasta las 14:30: no se puede abrir otra sesión a las 14:00")
    void rejectsOverlapWithinTheSameOffering() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, at(13, 0))));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.addAvailabilitySlot(java.id(), at(14, 0), OWNER));

        assertTrue(error.getMessage().contains("13:00 a 14:30"), error.getMessage());
        verify(slots, never()).save(any());
    }

    @Test
    @DisplayName("Una sesión que termina después de que empieza la siguiente también se cruza")
    void rejectsSlotThatEndsAfterTheNextStarts() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, at(15, 0))));

        assertThrows(BusinessRuleException.class, () -> service.addAvailabilitySlot(java.id(), at(14, 0), OWNER));
    }

    @Test
    @DisplayName("El mentor no puede dar dos mentorías distintas a la vez")
    void rejectsOverlapWithAnotherOfferingOfTheSameProvider() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of());
        when(slots.findByOfferingId(angular.id())).thenReturn(List.of(slot(angular, at(14, 0))));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.addAvailabilitySlot(java.id(), at(13, 0), OWNER));

        assertTrue(error.getMessage().contains("«Angular»"), error.getMessage());
    }

    @Test
    @DisplayName("Una sesión puede empezar justo cuando termina la anterior")
    void allowsBackToBackSessions() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, at(13, 0))));

        AvailabilitySlot saved = service.addAvailabilitySlot(java.id(), at(14, 30), OWNER);

        assertEquals(at(14, 30), saved.scheduledAt());
    }

    @Test
    @DisplayName("Horarios que no se tocan se publican sin problema")
    void allowsSeparatedSessions() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, at(9, 0))));

        assertDoesNotThrow(() -> service.addAvailabilitySlot(java.id(), at(13, 0), OWNER));
    }

    // ==========================================
    // PUBLICAR EN LOTE (LAS MISMAS HORAS EN VARIOS DÍAS)
    // ==========================================

    /** Dentro de {@code days} días a la hora indicada, en la hora de Colombia */
    private static Instant inDays(int days, int hour, int minute) {
        return LocalDate.now(ProviderOfferingService.BUSINESS_ZONE).plusDays(days)
                .atTime(LocalTime.of(hour, minute)).atZone(ProviderOfferingService.BUSINESS_ZONE).toInstant();
    }

    @Test
    @DisplayName("Lote: publica las mismas horas en varios días y omite las que ya estaban publicadas")
    void batchPublishesAndSkipsAlreadyPublished() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, inDays(2, 9, 0))));

        var result = service.addAvailabilitySlots(java.id(),
                List.of(inDays(2, 9, 0), inDays(2, 14, 0), inDays(3, 9, 0), inDays(3, 14, 0)), OWNER);

        assertEquals(List.of(inDays(2, 14, 0), inDays(3, 9, 0), inDays(3, 14, 0)),
                result.created().stream().map(AvailabilitySlot::scheduledAt).toList());
        assertEquals(1, result.skipped().size());
        assertEquals("Ya estaba publicado", result.skipped().get(0).reason());
        verify(slots, times(3)).save(any());
        verify(cache, times(1)).evictActiveOfferings();
    }

    @Test
    @DisplayName("Lote: omite los cruces con otra mentoría del proveedor y entre los horarios del mismo lote")
    void batchSkipsOverlaps() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of());
        when(slots.findByOfferingId(angular.id())).thenReturn(List.of(slot(angular, inDays(2, 14, 0))));

        // 13:00 (90 min) choca con Angular a las 14:00; 9:00 y 10:00 chocan entre sí: se publica la primera
        var result = service.addAvailabilitySlots(java.id(),
                List.of(inDays(2, 13, 0), inDays(2, 10, 0), inDays(2, 9, 0)), OWNER);

        assertEquals(List.of(inDays(2, 9, 0)), result.created().stream().map(AvailabilitySlot::scheduledAt).toList());
        assertEquals(2, result.skipped().size());
        assertTrue(result.skipped().stream().anyMatch(s -> s.reason().contains("«Angular»")), result.skipped().toString());
        assertTrue(result.skipped().stream().anyMatch(s -> s.reason().contains("otra sesión de esta mentoría")),
                result.skipped().toString());
    }

    @Test
    @DisplayName("Lote: omite fechas pasadas y días que la mentoría no ofrece, sin cancelar el resto")
    void batchSkipsPastAndNotOfferedDays() {
        Offering weekdaysOnly = new Offering(UUID.randomUUID(), providerId, "Solo lunes", "Mentoría", "BACKEND",
                BigDecimal.TEN, true, List.of(), 60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.MONDAY));
        when(offerings.findById(weekdaysOnly.id())).thenReturn(Optional.of(weekdaysOnly));
        when(offerings.findByProviderId(providerId)).thenReturn(List.of(weekdaysOnly));
        when(slots.findByOfferingId(weekdaysOnly.id())).thenReturn(List.of());

        Instant nextMonday = LocalDate.now(ProviderOfferingService.BUSINESS_ZONE)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(10, 0).atZone(ProviderOfferingService.BUSINESS_ZONE).toInstant();
        Instant nextTuesday = nextMonday.plus(Duration.ofDays(1));
        Instant yesterday = inDays(-1, 10, 0);

        var result = service.addAvailabilitySlots(weekdaysOnly.id(), List.of(yesterday, nextMonday, nextTuesday), OWNER);

        assertEquals(List.of(nextMonday), result.created().stream().map(AvailabilitySlot::scheduledAt).toList());
        assertEquals(List.of("La fecha ya pasó", "La mentoría no se ofrece los martes"),
                result.skipped().stream().map(ProviderOfferingService.SkippedSlot::reason).toList());
    }

    @Test
    @DisplayName("Lote: si nada se puede publicar no se toca la caché")
    void batchWithNothingToPublishDoesNotEvictCache() {
        when(slots.findByOfferingId(java.id())).thenReturn(List.of(slot(java, inDays(2, 9, 0))));

        var result = service.addAvailabilitySlots(java.id(), List.of(inDays(2, 9, 0)), OWNER);

        assertTrue(result.created().isEmpty());
        verify(cache, never()).evictActiveOfferings();
    }
}
