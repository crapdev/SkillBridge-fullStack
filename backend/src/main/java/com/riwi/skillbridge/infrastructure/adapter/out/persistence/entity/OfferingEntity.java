package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // ---- Ficha de la mentoría (HU-20, migración V8) ----

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private OfferingLevel level;

    // EAGER: la mentoría también se lee fuera de una petición HTTP (consumidores de RabbitMQ) y open-in-view está apagado
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "offering_topics", joinColumns = @JoinColumn(name = "offering_id"))
    @OrderColumn(name = "position")
    private List<SyllabusTopicEmbeddable> syllabus = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "offering_working_days", joinColumns = @JoinColumn(name = "offering_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private Set<DayOfWeek> workingDays = new HashSet<>();

    // ---- Revisión del administrador (HU-21, migración V9) ----

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private OfferingReviewStatus reviewStatus = OfferingReviewStatus.APPROVED;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    protected OfferingEntity() {}

    // Constructor público requerido por OfferingPersistenceAdapter
    public OfferingEntity(UUID id, UUID providerId, String title, String description, String category, BigDecimal price,
                          boolean active, List<SyllabusTopicEmbeddable> syllabus, int durationMinutes,
                          OfferingLevel level, Set<DayOfWeek> workingDays,
                          OfferingReviewStatus reviewStatus, String rejectionReason) {
        this.id = id;
        this.providerId = providerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.price = price;
        this.active = active;
        this.createdAt = Instant.now();
        updateProfile(syllabus, durationMinutes, level, workingDays);
        updateReview(reviewStatus, rejectionReason);
    }

    // Decisión del administrador, o vuelta a revisión cuando el proveedor corrige una mentoría rechazada
    public void updateReview(OfferingReviewStatus reviewStatus, String rejectionReason) {
        this.reviewStatus = reviewStatus;
        this.rejectionReason = rejectionReason;
    }

    // Edición del proveedor: no toca el dueño, el estado ni la fecha de creación
    public void updateDetails(String title, String description, String category, BigDecimal price) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.price = price;
    }

    // Se reemplaza el contenido de las colecciones (no la instancia) para que Hibernate sincronice las tablas hijas
    public void updateProfile(List<SyllabusTopicEmbeddable> syllabus, int durationMinutes, OfferingLevel level,
                              Set<DayOfWeek> workingDays) {
        this.syllabus.clear();
        this.syllabus.addAll(syllabus);
        this.durationMinutes = durationMinutes;
        this.level = level;
        this.workingDays.clear();
        this.workingDays.addAll(workingDays);
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<SyllabusTopicEmbeddable> getSyllabus() { return syllabus; }
    public int getDurationMinutes() { return durationMinutes; }
    public OfferingLevel getLevel() { return level; }
    public OfferingReviewStatus getReviewStatus() { return reviewStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public Set<DayOfWeek> getWorkingDays() {
        return workingDays.isEmpty() ? Set.of() : EnumSet.copyOf(workingDays);
    }
}
