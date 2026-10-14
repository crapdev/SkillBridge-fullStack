package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Offering;

import java.util.List;
import java.util.UUID;

/** HU-21: el administrador aprueba o rechaza cada mentoría nueva antes de que pueda llegar al catálogo. */
public interface ReviewOfferingsUseCase {

    /** Mentoría junto a quién la propone: el administrador necesita saber de quién es para revisarla. */
    record OfferingForReview(Offering offering, String providerName, String providerEmail) {}

    /** Pendientes de revisión, la que más lleva esperando primero. */
    List<OfferingForReview> listPending();

    OfferingForReview approve(UUID offeringId);

    /** El motivo es opcional; el proveedor lo ve en su panel para poder corregir la mentoría. */
    OfferingForReview reject(UUID offeringId, String reason);
}
