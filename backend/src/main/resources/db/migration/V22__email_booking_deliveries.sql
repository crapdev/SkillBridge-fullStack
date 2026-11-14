-- Idempotencia de los correos de reservas que envía el email-service (consumidor de booking.emails.queue).
-- Una fila por (reserva, evento, destinatario): RabbitMQ puede entregar el mismo evento más de una vez, y la
-- máquina de estados de la reserva garantiza que cada evento ocurre una sola vez por reserva.
-- No se guardan direcciones, nombres ni el cuerpo del correo: solo el rol del destinatario.
CREATE SCHEMA IF NOT EXISTS email;

CREATE TABLE email.booking_email_deliveries (
    id                  BIGSERIAL    PRIMARY KEY,
    booking_id          UUID         NOT NULL,
    event_type          VARCHAR(40)  NOT NULL CHECK (event_type IN
                            ('booking.created', 'booking.paid', 'booking.cancelled', 'booking.completed')),
    recipient_role      VARCHAR(20)  NOT NULL CHECK (recipient_role IN ('CUSTOMER', 'PROVIDER')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    attempts            INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error          VARCHAR(120),               -- código técnico, sin datos personales
    provider_message_id VARCHAR(255),               -- id que devuelva el proveedor de correo, si lo hay
    locked_until        TIMESTAMPTZ,                -- mientras está PROCESSING; vencido, otro intento puede retomarlo
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ,
    CONSTRAINT uq_booking_email_deliveries UNIQUE (booking_id, event_type, recipient_role)
);

CREATE INDEX idx_booking_email_deliveries_status ON email.booking_email_deliveries (status, updated_at);
