package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AvailabilitySlot(
    UUID id,
    UUID offeringId,
    Instant scheduledAt,
    boolean reserved
) { }
