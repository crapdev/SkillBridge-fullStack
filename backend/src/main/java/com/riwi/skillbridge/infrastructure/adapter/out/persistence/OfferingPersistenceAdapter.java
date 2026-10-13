package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.SyllabusTopicEmbeddable;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
            offering.active(),
            toEmbeddables(offering.syllabus()),
            offering.durationMinutes(),
            offering.level(),
            offering.workingDays()
        );
        OfferingEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Offering update(Offering offering) {
        // Se modifica la entidad existente: guardar una nueva reiniciaría created_at
        OfferingEntity entity = repository.findById(offering.id())
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));
        entity.updateDetails(offering.title(), offering.description(), offering.category(), offering.price());
        entity.updateProfile(toEmbeddables(offering.syllabus()), offering.durationMinutes(), offering.level(),
            offering.workingDays());
        return toDomain(repository.save(entity));
    }

    @Override
    public List<Offering> findVisible() {
        return repository.findVisibleOfferings(Instant.now())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Page<Offering> findVisible(Pageable pageable) {
        return repository.findVisibleOfferings(Instant.now(), pageable)
            .map(this::toDomain);
    }

    @Override
    public Optional<Offering> findVisibleById(UUID id) {
        return repository.findVisibleOfferingById(id, Instant.now())
            .map(this::toDomain);
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
            e.isActive(),
            e.getSyllabus().stream()
                .map(t -> new SyllabusTopic(t.getTitle(), t.getDescription()))
                .toList(),
            e.getDurationMinutes(),
            e.getLevel(),
            e.getWorkingDays()
        );
    }

    private static List<SyllabusTopicEmbeddable> toEmbeddables(List<SyllabusTopic> syllabus) {
        return syllabus.stream()
            .map(t -> new SyllabusTopicEmbeddable(t.title(), t.description()))
            .toList();
    }
}
