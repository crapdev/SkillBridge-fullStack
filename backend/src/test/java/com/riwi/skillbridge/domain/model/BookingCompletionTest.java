package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** BUG-05: una reserva confirmada pasa a COMPLETED cuando termina su sesión (hora agendada + duración). */
class BookingCompletionTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-20T15:00:00Z");
    private static final int DURATION = 60;

    private static Booking booking(BookingStatus status) {
        return new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), SCHEDULED_AT, status);
    }

    private static Instant at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC).instant();
    }

    @Test
    void confirmedBookingWhoseSessionEnded_isCompleted() {
        Booking confirmed = booking(BookingStatus.CONFIRMED);

        Booking completed = confirmed.complete(at("2026-10-20T16:00:01Z"), DURATION);

        assertEquals(BookingStatus.COMPLETED, completed.status());
        assertEquals(confirmed.id(), completed.id());
        assertEquals(SCHEDULED_AT, completed.scheduledAt());
    }

    @Test
    void sessionEndsAtScheduledTimePlusDuration() {
        assertEquals(SCHEDULED_AT.plus(90, ChronoUnit.MINUTES), booking(BookingStatus.CONFIRMED).endsAt(90));
    }

    @Test
    void exactlyAtTheEnd_theSessionIsFinished() {
        Booking confirmed = booking(BookingStatus.CONFIRMED);

        assertTrue(confirmed.isFinished(at("2026-10-20T16:00:00Z"), DURATION));
        assertEquals(BookingStatus.COMPLETED, confirmed.complete(at("2026-10-20T16:00:00Z"), DURATION).status());
    }

    @Test
    void sessionInProgress_cannotBeCompleted() {
        Booking confirmed = booking(BookingStatus.CONFIRMED);
        Instant duringTheSession = at("2026-10-20T15:30:00Z");

        assertFalse(confirmed.isFinished(duringTheSession, DURATION));
        assertThrows(BusinessRuleException.class, () -> confirmed.complete(duringTheSession, DURATION));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CANCELLED", "COMPLETED"})
    void onlyConfirmedBookingsCanBeCompleted(BookingStatus status) {
        Booking booking = booking(status);
        Instant longAfter = at("2026-10-21T15:00:00Z");

        assertFalse(booking.isFinished(longAfter, DURATION));
        assertThrows(BusinessRuleException.class, () -> booking.complete(longAfter, DURATION));
    }
}
