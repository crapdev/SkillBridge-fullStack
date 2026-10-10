
CREATE TABLE payments (
                        id UUID PRIMARY KEY,
                        booking_id UUID NOT NULL REFERENCES bookings(id),
                        amount NUMERIC(12,2) NOT NULL,
                        currency VARCHAR(3) NOT NULL DEFAULT 'COP',
                        stripe_payment_intent_id VARCHAR(255) UNIQUE,
                        status VARCHAR(30) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices cruciales para rendimiento
CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_stripe_intent ON payments(stripe_payment_intent_id);

