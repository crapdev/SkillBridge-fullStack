package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListMyBookingsUseCase;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final CreateBookingUseCase useCase;
    private final ListMyBookingsUseCase listMyBookingsUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(CreateBookingUseCase useCase, ListMyBookingsUseCase listMyBookingsUseCase, CancelBookingUseCase cancelBookingUseCase) { 
        this.useCase = useCase;
        this.listMyBookingsUseCase = listMyBookingsUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication) {
        return useCase.create(request.offeringId(), request.scheduledAt(), authentication.getName());
    }

    @GetMapping("/me")
    public List<Booking> listMyBookings(Authentication authentication) {
        return listMyBookingsUseCase.listMyBookings(authentication.getName());
    }

    @PatchMapping("/{id}/cancel")
    public Booking cancelBooking(@PathVariable UUID id, Authentication authentication) {
        return cancelBookingUseCase.cancelBooking(id, authentication.getName());
    }
}
