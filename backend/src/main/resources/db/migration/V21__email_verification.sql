CREATE SCHEMA IF NOT EXISTS email;

-- Registro sin verificar (HU #39). La cuenta no existe en app_users hasta que se verifica el correo.
CREATE TABLE email.pending_registrations (
    id               UUID PRIMARY KEY,
    email            VARCHAR(180) NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(30)  NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER')),
    token_hash       CHAR(64)     NOT NULL UNIQUE,   -- SHA-256 en hex del token; nunca el token
    token_expires_at TIMESTAMPTZ  NOT NULL,
    verified_at      TIMESTAMPTZ,                    -- se rellena al verificar; permite responder TOKEN_USED
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Peticiones de reenvío, para el límite de 3 cada 15 minutos (también de correos que no existen)
CREATE TABLE email.verification_resend_requests (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(180) NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_resend_requests_email_time ON email.verification_resend_requests (email, requested_at);
