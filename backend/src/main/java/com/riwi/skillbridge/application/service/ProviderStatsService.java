package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** HU-22: indicadores del dashboard del proveedor. El proveedor sale del token: nadie ve los datos de otro. */
@Service
public class ProviderStatsService implements GetProviderStatsUseCase {
    private final UserAccountPort users;
    private final OfferingRepositoryPort offerings;
    private final AvailabilitySlotRepositoryPort slots;
    private final BookingRepositoryPort bookings;
    private final CompleteFinishedBookingsUseCase completion;

    public ProviderStatsService(UserAccountPort users, OfferingRepositoryPort offerings,
                                AvailabilitySlotRepositoryPort slots, BookingRepositoryPort bookings,
                                CompleteFinishedBookingsUseCase completion) {
        this.users = users;
        this.offerings = offerings;
        this.slots = slots;
        this.bookings = bookings;
        this.completion = completion;
    }

    @Override
    public ProviderStats statsFor(String providerEmail) {
        UUID providerId = users.findIdByEmail(providerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        // Un mismo "ahora" para todo el cálculo: los conteos de "próximos" no cambian a mitad de camino
        Instant now = Instant.now();

        List<Offering> own = offerings.findByProviderId(providerId);
        // Horarios futuros de cada mentoría (un proveedor tiene pocas: una consulta por mentoría)
        Map<UUID, List<AvailabilitySlot>> upcomingSlots = own.stream().collect(Collectors.toMap(
            Offering::id,
            o -> slots.findByOfferingId(o.id()).stream().filter(s -> s.scheduledAt().isAfter(now)).toList()));
        // BUG-05: el conteo de completadas no depende de que el proceso programado ya haya corrido
        List<Booking> received = completion.completeIfFinished(bookings.findByOfferingIds(upcomingSlots.keySet()));

        return new ProviderStats(offeringCounts(own, upcomingSlots), slotCounts(upcomingSlots), bookingCounts(received, now));
    }

    private static OfferingCounts offeringCounts(List<Offering> own, Map<UUID, List<AvailabilitySlot>> upcomingSlots) {
        int withoutFreeSlots = (int) own.stream()
            .filter(Offering::isApproved)
            .filter(o -> upcomingSlots.get(o.id()).stream().allMatch(AvailabilitySlot::reserved))
            .count();
        return new OfferingCounts(own.size(),
            count(own, o -> o.reviewStatus() == OfferingReviewStatus.APPROVED),
            count(own, o -> o.reviewStatus() == OfferingReviewStatus.PENDING_REVIEW),
            count(own, o -> o.reviewStatus() == OfferingReviewStatus.REJECTED),
            withoutFreeSlots);
    }

    private static SlotCounts slotCounts(Map<UUID, List<AvailabilitySlot>> upcomingSlots) {
        List<AvailabilitySlot> all = upcomingSlots.values().stream().flatMap(List::stream).toList();
        return new SlotCounts(count(all, s -> !s.reserved()), count(all, AvailabilitySlot::reserved));
    }

    private static BookingCounts bookingCounts(List<Booking> received, Instant now) {
        return new BookingCounts(received.size(),
            // Próximas: las que siguen en pie (pendientes de pago o confirmadas) y aún no ocurren
            count(received, b -> (b.status() == BookingStatus.CREATED || b.status() == BookingStatus.CONFIRMED)
                && b.scheduledAt().isAfter(now)),
            count(received, b -> b.status() == BookingStatus.CREATED),
            count(received, b -> b.status() == BookingStatus.CONFIRMED),
            count(received, b -> b.status() == BookingStatus.COMPLETED),
            count(received, b -> b.status() == BookingStatus.CANCELLED));
    }

    private static <T> int count(List<T> items, Predicate<T> condition) {
        return (int) items.stream().filter(condition).count();
    }
}
