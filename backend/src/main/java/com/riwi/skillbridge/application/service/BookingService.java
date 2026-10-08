package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListMyBookingsUseCase;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService implements CreateBookingUseCase, ListMyBookingsUseCase, CancelBookingUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserAccountPort userAccountPort;
    private final BookingEventPublisherPort eventPublisher;

    public BookingService(BookingRepositoryPort bookingRepository,
                          OfferingRepositoryPort offeringRepository,
                          UserAccountPort userAccountPort,
                          BookingEventPublisherPort eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.offeringRepository = offeringRepository;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }

        Offering offering = offeringRepository.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!offering.active()) {
            throw new BusinessRuleException("El servicio no está activo");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        if(bookingRepository.existsDuplicateBooking(customerId, offeringId, scheduledAt)) {
            throw new BusinessRuleException("La reserva ya existe");
        }

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

    @Override
    public Booking cancelBooking(UUID bookingId, String customerEmail) {
        // 1. Obtener usuario autenticado
        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 2. Buscar la reserva
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        // 3. Verificar propiedad
        if (!booking.customerId().equals(customerId)) {
            throw new com.riwi.skillbridge.domain.exception.UnauthorizedActionException("No tienes permiso para cancelar esta reserva");
        }

        // 4. Ejecutar la lógica de dominio (retorna nueva reserva con estado actualizado)
        Booking cancelledBooking = booking.cancel(Instant.now());

        // 5. Persistir la modificación y retornar
        return bookingRepository.save(cancelledBooking);
    }
}
