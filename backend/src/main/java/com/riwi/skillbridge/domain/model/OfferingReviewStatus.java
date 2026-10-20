package com.riwi.skillbridge.domain.model;

/** Revisión de una mentoría por el administrador (HU-21): solo las aprobadas pueden llegar al catálogo. */
public enum OfferingReviewStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
