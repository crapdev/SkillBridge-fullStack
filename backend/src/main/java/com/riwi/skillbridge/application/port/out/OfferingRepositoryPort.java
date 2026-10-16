package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
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
    List<Offering> findVisible();
    Page<Offering> findVisible(Pageable pageable);
    Optional<Offering> findVisibleById(UUID id);
    List<Offering> findByProviderId(UUID providerId);
    /** HU-21: mentorías en un estado de revisión, la más antigua primero (la cola del administrador). */
    List<Offering> findByReviewStatus(OfferingReviewStatus reviewStatus);
    void deleteById(UUID id);
}
