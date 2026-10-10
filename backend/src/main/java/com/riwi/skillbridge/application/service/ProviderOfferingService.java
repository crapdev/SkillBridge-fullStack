package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ListAvailableSlotsUseCase;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
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
public class ProviderOfferingService implements ListAvailableSlotsUseCase {

    private final OfferingRepositoryPort offeringRepositoryPort;
    private final AvailabilitySlotRepositoryPort slotRepositoryPort;
    private final UserAccountPort userAccountPort;
    private final OfferingCachePort offeringCache;

    public ProviderOfferingService(
        OfferingRepositoryPort offeringRepositoryPort,
        AvailabilitySlotRepositoryPort slotRepositoryPort,
        UserAccountPort userAccountPort,
        OfferingCachePort offeringCache
    ) {
        this.offeringRepositoryPort = offeringRepositoryPort;
        this.slotRepositoryPort = slotRepositoryPort;
        this.userAccountPort = userAccountPort;
        this.offeringCache = offeringCache;
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
        Offering saved = offeringRepositoryPort.save(offering);
        // El catálogo público está en caché: sin invalidarlo, la mentoría nueva no aparece hasta que expire
        offeringCache.evictActiveOfferings();
        return saved;
    }

    // NUEVO MÉTODO: Trae la lista de mentorías de este proveedor específico
    public List<Offering> getOwnedOfferings(String providerEmail) {
        // 1. Buscamos el ID del proveedor usando su correo
        UUID providerId = getProviderId(providerEmail);

        // 2. Buscamos todas las mentorías asociadas a ese ID
        return offeringRepositoryPort.findByProviderId(providerId);
    }

    // DELETE OFFERING (VALIDATE THE OWNER IS PROVIDER)
    public void deleteOffering(UUID offeringId, String providerEmail) {
        Offering existing = getOwnedOffering(offeringId, providerEmail);
        // Los horarios dependen de la mentoría: sin borrarlos antes, la base de datos rechaza la eliminación
        if (slotRepositoryPort.findByOfferingId(existing.id()).stream().anyMatch(AvailabilitySlot::reserved)) {
            throw new BusinessRuleException("No puedes eliminar una mentoría que tiene horarios reservados");
        }
        slotRepositoryPort.deleteByOfferingId(existing.id());
        offeringRepositoryPort.deleteById(existing.id());
        offeringCache.evictActiveOfferings();
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

    // SHOW AVAILABLE SLOTS TO CUSTOMERS (ONLY NOT RESERVED AND IN THE FUTURE)
    @Override
    public List<AvailabilitySlot> listAvailableSlots(UUID offeringId) {
        Instant now = Instant.now();
        return slotRepositoryPort.findAvailableByOfferingId(offeringId).stream()
            .filter(slot -> slot.scheduledAt().isAfter(now))
            .toList();
    }

    // LIST THE PROVIDER'S UPCOMING SLOTS (FREE AND RESERVED) FOR ONE OF THEIR OFFERINGS
    public List<AvailabilitySlot> listOwnedSlots(UUID offeringId, String providerEmail) {
        getOwnedOffering(offeringId, providerEmail);
        Instant now = Instant.now();
        return slotRepositoryPort.findByOfferingId(offeringId).stream()
            .filter(slot -> slot.scheduledAt().isAfter(now))
            .toList();
    }

    // DELETE A FREE SLOT (A RESERVED ONE ALREADY HAS A CUSTOMER BOOKING)
    public void deleteAvailabilitySlot(UUID offeringId, UUID slotId, String providerEmail) {
        getOwnedOffering(offeringId, providerEmail);
        AvailabilitySlot slot = slotRepositoryPort.findById(slotId)
            .filter(s -> s.offeringId().equals(offeringId))
            .orElseThrow(() -> new DomainNotFoundException("Horario no encontrado"));
        if (slot.reserved()) {
            throw new BusinessRuleException("No puedes eliminar un horario que ya fue reservado");
        }
        slotRepositoryPort.deleteById(slotId);
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
