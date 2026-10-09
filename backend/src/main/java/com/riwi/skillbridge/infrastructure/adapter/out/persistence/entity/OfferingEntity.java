package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offerings")
public class OfferingEntity {
    @Id
    private UUID id;
    private UUID providerId;
    private String title;
    private String description;
    private String category;
    private BigDecimal price;
    private boolean active;
    @Column(name = "created_at")
    private Instant createdAt;

    protected OfferingEntity() {}

    // Constructor público requerido por OfferingPersistenceAdapter
    public OfferingEntity(UUID id, UUID providerId, String title, String description, String category, BigDecimal price, boolean active) {
        this.id = id;
        this.providerId = providerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.price = price;
        this.active = active;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
