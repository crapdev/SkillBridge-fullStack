package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.service.ProviderOfferingService;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateSlotRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/provider/offerings")
public class ProviderOfferingController {

    private final ProviderOfferingService providerOfferingService;

    public ProviderOfferingController(ProviderOfferingService providerOfferingService) {
        this.providerOfferingService = providerOfferingService;
    }

    // DTO local para recibir los datos exactos que envía tu formulario de Angular
    public record CreateOfferingRequest(String title, String description, String category, BigDecimal price) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Offering createOffering(@RequestBody CreateOfferingRequest request, Authentication authentication) {
        // authentication.getName() saca el email del token automáticamente
        return providerOfferingService.createOffering(
                request.title(),
                request.description(),
                request.category(),
                request.price(),
                authentication.getName() 
        );
    }

    @GetMapping
    public List<Offering> listMyOfferings(Authentication authentication) {
        // Llama al servicio para obtener solo las mentorías de este proveedor
        return providerOfferingService.getOwnedOfferings(authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffering(@PathVariable UUID id, Authentication authentication) {
        providerOfferingService.deleteOffering(id, authentication.getName());
    }

    // ---- Horarios disponibles de una mentoría del proveedor ----

    @GetMapping("/{id}/slots")
    public List<AvailabilitySlot> listSlots(@PathVariable UUID id, Authentication authentication) {
        return providerOfferingService.listOwnedSlots(id, authentication.getName());
    }

    @PostMapping("/{id}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilitySlot addSlot(@PathVariable UUID id, @Valid @RequestBody CreateSlotRequest request,
                                    Authentication authentication) {
        return providerOfferingService.addAvailabilitySlot(id, request.scheduledAt(), authentication.getName());
    }

    @DeleteMapping("/{id}/slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable UUID id, @PathVariable UUID slotId, Authentication authentication) {
        providerOfferingService.deleteAvailabilitySlot(id, slotId, authentication.getName());
    }
}