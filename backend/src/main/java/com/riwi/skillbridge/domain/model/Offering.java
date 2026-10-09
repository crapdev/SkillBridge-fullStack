package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Offering(
        UUID id,
        UUID providerId,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active
) {}
