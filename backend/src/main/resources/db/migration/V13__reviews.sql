-- HU-27: calificaciones de mentorías y mentores.
-- Número reservado en Jira (SCRUM-206). Las columnas id, booking_id, rating, comment y created_at son las del
-- contrato REVIEWS de data/src/skillbridge_data/schemas.py (HU-47); mentor_rating y mentor_comment son la
-- calificación del mentor, que el contrato de datos no exporta.
CREATE TABLE reviews (
    id             UUID PRIMARY KEY,
    booking_id     UUID         NOT NULL REFERENCES bookings(id),
    -- Calificación de la mentoría (la sesión)
    rating         INTEGER      NOT NULL CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    comment        VARCHAR(500),
    -- Calificación del mentor (el proveedor que dio la sesión)
    mentor_rating  INTEGER      NOT NULL CONSTRAINT chk_reviews_mentor_rating CHECK (mentor_rating BETWEEN 1 AND 5),
    mentor_comment VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Una reserva se califica una sola vez. Si dos peticiones llegan a la vez, la base deja pasar solo una
    CONSTRAINT uq_reviews_booking UNIQUE (booking_id)
);

-- El detalle pide "las últimas calificaciones": se ordenan por fecha
CREATE INDEX idx_reviews_created ON reviews(created_at DESC);
