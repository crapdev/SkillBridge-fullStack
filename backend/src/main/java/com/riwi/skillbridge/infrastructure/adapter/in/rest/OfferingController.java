package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListAvailableSlotsUseCase;
import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offerings")
public class OfferingController {
    private final ListOfferingsUseCase useCase;
    private final ListAvailableSlotsUseCase slots;

    public OfferingController(ListOfferingsUseCase useCase, ListAvailableSlotsUseCase slots) {
        this.useCase = useCase;
        this.slots = slots;
    }

    @GetMapping
    public List<Offering> list() { return useCase.listActive(); }

    // Público: el cliente elige entre los horarios que el proveedor publicó
    @GetMapping("/{id}/slots")
    public List<AvailabilitySlot> availableSlots(@PathVariable UUID id) { return slots.listAvailableSlots(id); }
}
