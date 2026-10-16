package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListAvailableSlotsUseCase;
import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Page<Offering> list(
            @PageableDefault(size = 9, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return useCase.listVisible(pageable);
    }

    @GetMapping("/{id}")
    public Offering getById(@PathVariable UUID id) {
        return useCase.getVisibleById(id);
    }

    // Público: el cliente elige entre los horarios que el proveedor publicó
    @GetMapping("/{id}/slots")
    public List<AvailabilitySlot> availableSlots(@PathVariable UUID id) {
        return slots.listAvailableSlots(id);
    }
}
