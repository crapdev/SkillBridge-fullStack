-- 1. Tabla principal de usuarios (Todos los roles)
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_role CHECK (role IN ('CUSTOMER', 'PROVIDER', 'ADMIN'))
);

-- 2. Perfil específico para proveedores (Relación 1:1 con app_users)
CREATE TABLE provider_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    biography TEXT,
    hourly_rate NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Ofertas/Mentorías (Ahora asociadas a un proveedor)
CREATE TABLE offerings (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1200) NOT NULL,
    category VARCHAR(80) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Reservas (Bookings)
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES offerings(id),
    customer_id UUID NOT NULL REFERENCES app_users(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_status CHECK (status IN ('CREATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'))
);

-- 5. Índices para optimizar consultas frecuentes
CREATE INDEX idx_offerings_active ON offerings(id) WHERE active = true; -- Índice parcial optimizado
CREATE INDEX idx_offerings_provider ON offerings(provider_id);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_offering ON bookings(offering_id);

-- (Opcional) Datos de prueba iniciales adaptados al nuevo modelo
-- Aquí tendrías que insertar primero un usuario con rol PROVIDER, luego su perfil y luego las ofertas.

-- 6. Creación del Administrador por defecto
-- Nota: La contraseña DEBE ser un hash generado por BCrypt (ej. de 'admin123').
-- Usamos un UUID estático para evitar que cambie si se reconstruye la base de datos.
INSERT INTO app_users (id, name, email, password, role) 
VALUES (
    '00000000-0000-0000-0000-000000000001', 
    'Administrador Principal', 
    'admin@skillbridge.com', 
    '$2a$10$TuHashGeneradoConBcryptAqui...', 
    'ADMIN'
) ON CONFLICT (email) DO NOTHING;