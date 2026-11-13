-- BUG-11: la base de datos no acepta datos imposibles.

-- 1. Las 3 mentorías de ejemplo de V1 no tenían proveedor (V2 agregó offerings.provider_id como anulable y no se
--    lo asignó). Se asignan al proveedor de ejemplo que crea V5. Si alguien lo borró, se vuelve a crear SIN acceso:
--    la contraseña no es un hash BCrypt, así que nadie puede iniciar sesión con esa cuenta.
INSERT INTO app_users (id, name, email, password, role, status)
VALUES (gen_random_uuid(), 'Proveedor Demo', 'proveedor.demo@skillbridge.dev', '!sin-acceso', 'PROVIDER', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_users WHERE email = 'proveedor.demo@skillbridge.dev' AND role <> 'PROVIDER') THEN
        RAISE EXCEPTION 'BUG-11: proveedor.demo@skillbridge.dev existe pero no es PROVIDER. Revisa esa cuenta antes de aplicar V13';
    END IF;
END $$;

UPDATE offerings
SET provider_id = (SELECT id FROM app_users WHERE email = 'proveedor.demo@skillbridge.dev')
WHERE provider_id IS NULL;

-- Desde HU-19 toda mentoría la crea un proveedor: ya no puede haber una sin dueño
ALTER TABLE offerings ALTER COLUMN provider_id SET NOT NULL;

-- 2. Los estados solo pueden ser los de los enums del dominio (BookingStatus y PaymentStatus), igual que
--    offerings.level y offerings.review_status desde V8 y V9. Si HU-37 agrega estados, debe cambiar estos CHECK
--    en su propia migración.
--    Si la migración falla aquí, hay filas con estados inventados. Para encontrarlas:
--      SELECT status, COUNT(id) FROM bookings GROUP BY status;
--      SELECT status, COUNT(id) FROM payments GROUP BY status;
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_status
    CHECK (status IN ('CREATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));

ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELED'));
