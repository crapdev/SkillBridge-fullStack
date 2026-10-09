package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    Offering save(Offering offering);
    Optional<Offering> findById(UUID id);
    List<Offering> findAllActive();
    List<Offering> findByProviderId(UUID providerId);
    void deleteById(UUID id);
}
