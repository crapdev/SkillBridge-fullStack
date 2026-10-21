package com.riwi.skillbridge.application.service;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessTimeTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // 19/10/2026 23:30 en Bogotá = 20/10/2026 04:30 en UTC
    private static final Instant PAGO_NOCTURNO = Instant.parse("2026-10-20T04:30:00Z");

    @Test
    void paymentAt2330BogotaBelongsToThatSameDay() {
        BusinessTime time = new BusinessTime(Clock.fixed(PAGO_NOCTURNO, ZoneOffset.UTC), BOGOTA);

        assertEquals(LocalDate.of(2026, 10, 19), time.today());
        assertEquals(LocalDate.of(2026, 10, 19), time.toLocalDate(PAGO_NOCTURNO));
    }

    @Test
    void utcDateIsTheNextDay_documentsTheBug() {
        // Lo que pasa hoy al interpretar el instante sin zona de negocio (servidor de Render en UTC)
        assertEquals(LocalDate.of(2026, 10, 20), LocalDate.ofInstant(PAGO_NOCTURNO, ZoneOffset.UTC));
    }

    @Test
    void startOfMonthUsesBogotaMidnight() {
        // 31/10 23:30 en Bogotá: todavía es octubre aunque en UTC ya sea noviembre
        BusinessTime time = new BusinessTime(
            Clock.fixed(Instant.parse("2026-11-01T04:30:00Z"), ZoneOffset.UTC), BOGOTA);

        assertEquals(Instant.parse("2026-10-01T05:00:00Z"), time.startOfMonth());
    }
}
