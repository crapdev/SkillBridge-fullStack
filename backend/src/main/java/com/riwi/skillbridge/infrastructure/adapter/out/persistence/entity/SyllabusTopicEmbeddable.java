package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Fila de la tabla offering_topics: un tema del temario de la mentoría (HU-20). */
@Embeddable
public class SyllabusTopicEmbeddable {
    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    protected SyllabusTopicEmbeddable() {}

    public SyllabusTopicEmbeddable(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
