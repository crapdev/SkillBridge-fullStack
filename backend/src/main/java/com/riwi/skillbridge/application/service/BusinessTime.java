package com.riwi.skillbridge.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reloj y zona horaria de negocio. Los instantes se guardan en UTC; todo lo que se muestra
 * o se agrupa por día ("hoy", "este mes") se interpreta en la zona de negocio.
 */
public class BusinessTime {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessTime(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(now(), zone);
    }

    public LocalDate toLocalDate(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    /** Primer instante del mes en curso según la zona de negocio (medianoche del día 1). */
    public Instant startOfMonth() {
        return today().withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }
}
