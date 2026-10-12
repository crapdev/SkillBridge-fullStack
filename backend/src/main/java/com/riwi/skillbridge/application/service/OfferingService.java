package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OfferingService implements ListOfferingsUseCase {
    private final OfferingRepositoryPort repository;
    private final OfferingCachePort cache;

    public OfferingService(OfferingRepositoryPort repository, OfferingCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public List<Offering> listActive() {
        return cache.getActiveOfferings().orElseGet(() -> {
            List<Offering> offerings = repository.findVisible();
            cache.putActiveOfferings(offerings);
            return offerings;
        });
    }

    @Override
    public Page<Offering> listVisible(Pageable pageable) {
        return repository.findVisible(pageable);
    }

    @Override
    public Offering getVisibleById(UUID id) {
        return repository.findVisibleById(id)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));
    }
}
