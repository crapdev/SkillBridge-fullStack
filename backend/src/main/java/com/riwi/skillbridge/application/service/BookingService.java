package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListMyBookingsUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService implements CreateBookingUseCase,  ListMyBookingsUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserAccountPort userAccountPort;
    private final BookingEventPublisherPort eventPublisher;
    private final AvailabilitySlotRepositoryPort slotRepository;

    public BookingService(BookingRepositoryPort bookingRepository,
                          OfferingRepositoryPort offeringRepository,
                          UserAccountPort userAccountPort,
                          BookingEventPublisherPort eventPublisher, AvailabilitySlotRepositoryPort slotRepository) {
        this.bookingRepository = bookingRepository;
        this.offeringRepository = offeringRepository;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
        this.slotRepository = slotRepository;
    }

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }

        Offering offering = offeringRepository.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));
        if (!offering.active()) {
            throw new BusinessRuleException("La mentoría no está activa");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        AvailabilitySlot slot = slotRepository.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)
            .orElseThrow(() -> new BusinessRuleException("El proveedor no tiene habilitado este horario"));

        if (slot.reserved() || bookingRepository.existsByOfferingIdAndScheduledAt(offeringId, scheduledAt)) {
            throw new BusinessRuleException("Este horario ya fue reservado por otro usuario");
        }

        if (bookingRepository.existsByCustomerIdAndScheduledAt(customerId, scheduledAt)) {
            throw new BusinessRuleException("Ya tienes otra mentoría agendada en este mismo horario");
        }

        slotRepository.save(new AvailabilitySlot(slot.id(), slot.offeringId(), slot.scheduledAt(), true));

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customerId, scheduledAt, BookingStatus.CREATED);
        Booking saved = bookingRepository.save(booking);
        eventPublisher.bookingCreated(saved);
        return saved;
    }

    @Override
    public List<Booking> listMyBookings(String customerEmail) {
        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        return bookingRepository.findByCustomerId(customerId);
    }
}
