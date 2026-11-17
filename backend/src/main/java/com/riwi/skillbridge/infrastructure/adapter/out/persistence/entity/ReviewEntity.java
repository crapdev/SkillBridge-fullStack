package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** HU-27: tabla reviews (migración V20). */
@Entity
@Table(name = "reviews")
@Getter
@Setter
public class ReviewEntity {

    // El id lo asigna el dominio al crear la calificación
    @Id
    private UUID id;

    // Solo el id: guardar una calificación no necesita cargar la reserva completa
    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(nullable = false, updatable = false)
    private int rating;

    @Column(updatable = false)
    private String comment;

    @Column(name = "mentor_rating", nullable = false, updatable = false)
    private int mentorRating;

    @Column(name = "mentor_comment", updatable = false)
    private String mentorComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
