package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** HU-21: revisión de mentorías por el administrador. Las reglas (solo se revisa lo pendiente) viven en Offering. */
@Service
public class OfferingReviewService implements ReviewOfferingsUseCase {
    private final OfferingRepositoryPort offerings;
    private final UserRepositoryPort users;
    private final OfferingCachePort offeringCache;

    public OfferingReviewService(OfferingRepositoryPort offerings, UserRepositoryPort users,
                                 OfferingCachePort offeringCache) {
        this.offerings = offerings;
        this.users = users;
        this.offeringCache = offeringCache;
    }

    @Override
    public List<OfferingForReview> listPending() {
        return offerings.findByReviewStatus(OfferingReviewStatus.PENDING_REVIEW).stream()
            .map(this::withProvider)
            .toList();
    }

    @Override
    public OfferingForReview approve(UUID offeringId) {
        return save(find(offeringId).approve());
    }

    @Override
    public OfferingForReview reject(UUID offeringId, String reason) {
        return save(find(offeringId).reject(reason));
    }

    private Offering find(UUID offeringId) {
        return offerings.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));
    }

    private OfferingForReview save(Offering reviewed) {
        Offering saved = offerings.update(reviewed);
        // El catálogo público está en caché: una mentoría recién aprobada no aparecería hasta que expire
        offeringCache.evictActiveOfferings();
        return withProvider(saved);
    }

    private OfferingForReview withProvider(Offering offering) {
        UserAccount provider = offering.providerId() == null ? null
            : users.findById(offering.providerId()).orElse(null);
        return new OfferingForReview(offering,
            provider == null ? null : provider.name(),
            provider == null ? null : provider.email());
    }
}
