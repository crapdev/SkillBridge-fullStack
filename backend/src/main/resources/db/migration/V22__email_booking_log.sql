CREATE SCHEMA IF NOT EXISTS email;

-- Un registro por (reserva, evento, destinatario): evita correos repetidos si RabbitMQ entrega el evento dos veces (HU #38)
CREATE TABLE email.email_log (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   UUID        NOT NULL,
    routing_key  VARCHAR(40) NOT NULL,
    recipient_id UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'FAILED')),
    attempts     INT         NOT NULL DEFAULT 1,
    last_error   VARCHAR(300),            -- mensaje breve, sin datos personales
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_email_log UNIQUE (booking_id, routing_key, recipient_id)
);
