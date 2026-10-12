package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    Offering save(Offering offering);
    /** Actualiza título, descripción, categoría y precio conservando dueño, estado y fecha de creación. */
    Offering update(Offering offering);
    Optional<Offering> findById(UUID id);
    List<Offering> findAllActive();
    List<Offering> findVisible();
    Page<Offering> findVisible(Pageable pageable);
    Optional<Offering> findVisibleById(UUID id);
    List<Offering> findByProviderId(UUID providerId);
    void deleteById(UUID id);
}
