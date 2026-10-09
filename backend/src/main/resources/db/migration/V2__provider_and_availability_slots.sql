ALTER TABLE offerings ADD COLUMN provider_id UUID REFERENCES app_users(id);

CREATE INDEX idx_offerings_provider ON offerings(provider_id);

CREATE TABLE availability_slots (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES offerings(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    reserved BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_availability_slots_offering_time UNIQUE (offering_id, scheduled_at)
);
