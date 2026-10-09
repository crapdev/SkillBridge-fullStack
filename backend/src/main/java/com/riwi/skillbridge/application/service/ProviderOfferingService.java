package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderOfferingService {

    private final OfferingRepositoryPort offeringRepositoryPort;
    private final AvailabilitySlotRepositoryPort slotRepositoryPort;
    private final UserAccountPort userAccountPort;

    public ProviderOfferingService(
        OfferingRepositoryPort offeringRepositoryPort,
        AvailabilitySlotRepositoryPort slotRepositoryPort,
        UserAccountPort userAccountPort
    ) {
        this.offeringRepositoryPort = offeringRepositoryPort;
        this.slotRepositoryPort = slotRepositoryPort;
        this.userAccountPort = userAccountPort;
    }

    // CREATE OFFERING
    public Offering createOffering(String title, String description, String category, BigDecimal price, String providerEmail) {
        UUID providerId = getProviderId(providerEmail);
        Offering offering = new Offering(
            UUID.randomUUID(),
            providerId,
            title,
            description,
            category,
            price,
            true
        );
        return offeringRepositoryPort.save(offering);
    }

    // NUEVO MÉTODO: Trae la lista de mentorías de este proveedor específico
    public List<Offering> getOwnedOfferings(String providerEmail) {
        // 1. Buscamos el ID del proveedor usando su correo
        UUID providerId = userAccountPort.findIdByEmail(providerEmail)
            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado")); // Usa DomainNotFoundException si lo tienes importado

        // 2. Buscamos todas las mentorías asociadas a ese ID
        return offeringRepositoryPort.findByProviderId(providerId);
    }

    // DELETE OFFERING (VALIDATE THE OWNER IS PROVIDER)
    public void deleteOffering(UUID offeringId, String providerEmail) {
        Offering existing = getOwnedOffering(offeringId, providerEmail);
        offeringRepositoryPort.deleteById(existing.id());
    }

    // CREATE AVAILABILITY (ASSIGN DATE AND HOURS SPECIFIC TO THE OFFERING)
    public AvailabilitySlot addAvailabilitySlot(UUID offeringId, Instant scheduledAt, String providerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("El horario disponible debe ser en una fecha futura");
        }
        getOwnedOffering(offeringId, providerEmail);

        if (slotRepositoryPort.existsByOfferingIdAndScheduledAt(offeringId, scheduledAt)) {
            throw new BusinessRuleException("Ya registraste este horario para esta mentoría");
        }

        AvailabilitySlot slot = new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false);
        return slotRepositoryPort.save(slot);
    }

    // SHOW AVAILABLE SLOTS TO CUSTOMERS (ONLY NOT RESERVED)
    public List<AvailabilitySlot> listAvailableSlots(UUID offeringId) {
        return slotRepositoryPort.findAvailableByOfferingId(offeringId);
    }

    public Offering getOwnedOffering(UUID offeringId, String providerEmail) {
        UUID providerId = getProviderId(providerEmail);
        Offering offering = offeringRepositoryPort.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));

        if (!offering.providerId().equals(providerId)) {
            throw new BusinessRuleException("No tienes permisos para modificar una mentoría de otro proveedor");
        }
        return offering;
    }

    private UUID getProviderId(String email) {
        return userAccountPort.findIdByEmail(email)
            .orElseThrow(() -> new DomainNotFoundException("Proveedor no encontrado"));
    }
}
