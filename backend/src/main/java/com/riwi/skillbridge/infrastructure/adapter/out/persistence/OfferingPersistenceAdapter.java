package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OfferingPersistenceAdapter implements OfferingRepositoryPort {
    private final JpaOfferingRepository repository;

    public OfferingPersistenceAdapter(JpaOfferingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Offering save(Offering offering) {
        OfferingEntity entity = new OfferingEntity(
            offering.id(),
            offering.providerId(),
            offering.title(),
            offering.description(),
            offering.category(),
            offering.price(),
            offering.active()
        );
        OfferingEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<Offering> findAllActive() {
        return repository.findByActiveTrueOrderByTitleAsc()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<Offering> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Offering> findByProviderId(UUID providerId) {
        return repository.findByProviderId(providerId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(
            e.getId(),
            e.getProviderId(),
            e.getTitle(),
            e.getDescription(),
            e.getCategory(),
            e.getPrice(),
            e.isActive()
        );
    }
}
