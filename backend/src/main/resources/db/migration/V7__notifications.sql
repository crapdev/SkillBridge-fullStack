-- Notificaciones de reservas para clientes y proveedores (HU-11)
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES app_users(id),
    booking_id   UUID NOT NULL REFERENCES bookings(id),
    type         VARCHAR(40)  NOT NULL,
    message      VARCHAR(300) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at      TIMESTAMPTZ,
    -- Idempotencia: el mismo evento entregado dos veces no crea otra notificación
    CONSTRAINT uq_notifications_booking_type_recipient UNIQUE (booking_id, type, recipient_id)
);

-- La campana pide "las últimas N del usuario" y "cuántas no leídas"
CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_id) WHERE read_at IS NULL;
