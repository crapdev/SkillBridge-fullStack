package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BUG-05: una reserva confirmada pasa a COMPLETED cuando termina su sesión (hora agendada + duración de la
 * mentoría). Lo hace el proceso programado y también cada consulta de reservas, por si el proceso no ha corrido.
 */
@Service
public class BookingCompletionService implements CompleteFinishedBookingsUseCase {
    private final BookingRepositoryPort bookings;
    private final OfferingRepositoryPort offerings;
    private final BookingEventPublisherPort eventPublisher;
    private final Clock clock;

    public BookingCompletionService(BookingRepositoryPort bookings, OfferingRepositoryPort offerings,
                                    BookingEventPublisherPort eventPublisher, Clock clock) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public int completeFinished() {
        Instant now = clock.instant();
        // Candidatas: confirmadas que ya empezaron; la duración de cada mentoría dice si ya terminaron
        List<Booking> started = bookings.findConfirmedScheduledBefore(now);
        return (int) completeIfFinished(started, now).stream()
            .filter(b -> b.status() == BookingStatus.COMPLETED)
            .count();
    }

    @Override
    public List<Booking> completeIfFinished(List<Booking> list) {
        return completeIfFinished(list, clock.instant());
    }

    private List<Booking> completeIfFinished(List<Booking> list, Instant now) {
        // Varias reservas suelen ser de la misma mentoría: su duración se busca una sola vez
        Map<UUID, Integer> durations = new HashMap<>();
        return list.stream().map(b -> completeIfFinished(b, now, durations)).toList();
    }

    private Booking completeIfFinished(Booking booking, Instant now, Map<UUID, Integer> durations) {
        // Las que no están confirmadas o aún no empiezan no necesitan buscar la mentoría
        if (booking.status() != BookingStatus.CONFIRMED || booking.scheduledAt().isAfter(now)) {
            return booking;
        }
        int duration = durations.computeIfAbsent(booking.offeringId(), this::durationOf);
        if (!booking.isFinished(now, duration)) {
            return booking;
        }
        Booking completed = booking.complete(now, duration);
        // Solo quien hace el cambio en la base publica el evento: si el proceso programado y una consulta
        // la completan a la vez (o se reprocesa), el evento no se duplica
        if (bookings.updateStatusIf(booking.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED)) {
            eventPublisher.bookingCompleted(completed);
        }
        return completed;
    }

    // Una mentoría borrada no deja la reserva pendiente para siempre: se usa la duración por defecto
    private int durationOf(UUID offeringId) {
        return offerings.findById(offeringId)
            .map(Offering::durationMinutes)
            .orElse(Offering.DEFAULT_DURATION_MINUTES);
    }
}
