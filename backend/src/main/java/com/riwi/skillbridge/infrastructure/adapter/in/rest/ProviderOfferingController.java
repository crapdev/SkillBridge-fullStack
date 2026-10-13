package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.service.ProviderOfferingService;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateSlotRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/provider/offerings")
public class ProviderOfferingController {

    private final ProviderOfferingService providerOfferingService;

    public ProviderOfferingController(ProviderOfferingService providerOfferingService) {
        this.providerOfferingService = providerOfferingService;
    }

    // HU-20: crear una mentoría con su ficha completa. 400 si algún dato no es válido (p. ej. menos de 2 temas)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Offering createOffering(@Valid @RequestBody OfferingRequest request, Authentication authentication) {
        // authentication.getName() saca el email del token automáticamente
        return providerOfferingService.createOffering(request.toDraft(), authentication.getName());
    }

    @GetMapping
    public List<Offering> listMyOfferings(Authentication authentication) {
        // Llama al servicio para obtener solo las mentorías de este proveedor
        return providerOfferingService.getOwnedOfferings(authentication.getName());
    }

    // HU-19/HU-20: editar una mentoría propia y su ficha. 400 si los datos no son válidos,
    // 403 si es de otro proveedor, 404 si no existe
    @PutMapping("/{id}")
    public Offering updateOffering(@PathVariable UUID id, @Valid @RequestBody OfferingRequest request,
                                   Authentication authentication) {
        return providerOfferingService.updateOffering(id, request.toDraft(), authentication.getName());
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