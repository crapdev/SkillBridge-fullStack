-- Ejecutar una sola vez con los procesos del backend detenidos.
BEGIN;
SET LOCAL lock_timeout = '5s';
LOCK TABLE public.flyway_schema_history IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE bug13_versions (
    ordinal integer PRIMARY KEY,
    old_version varchar(50) UNIQUE,
    new_version varchar(50) UNIQUE,
    filename text
) ON COMMIT DROP;

INSERT INTO bug13_versions VALUES
    (1, '1', '1', 'init.sql'),
    (2, '2', '2', 'provider_and_availability_slots.sql'),
    (3, '4', '3', 'user_account_status.sql'),
    (4, '5', '4', 'seed_admin.sql'),
    (5, '6', '5', 'payments.sql'),
    (6, '7', '6', 'notifications.sql'),
    (7, '8', '7', 'offering_profile.sql'),
    (8, '9', '8', 'offering_review.sql'),
    (9, '10', '9', 'bookings_active_slot_unique.sql'),
    (10, '11', '10', 'payments_one_active_per_booking.sql'),
    (11, '12', '11', 'bookings_price.sql'),
    (12, '13', '12', 'data_constraints.sql'),
    (13, '20', '13', 'reviews.sql');

DO $$
BEGIN
    IF EXISTS (
        WITH history AS (
            SELECT row_number() OVER (ORDER BY installed_rank) AS ordinal, version, script, type, success, checksum
            FROM public.flyway_schema_history
        )
        SELECT 1
        FROM history h FULL JOIN bug13_versions m USING (ordinal)
        WHERE h.version IS DISTINCT FROM m.old_version
           OR h.script IS DISTINCT FROM ('V' || m.old_version || '__' || m.filename)
           OR h.type IS DISTINCT FROM 'SQL'
           OR h.success IS DISTINCT FROM true
           OR h.checksum IS NULL
    ) THEN
        RAISE EXCEPTION 'BUG-13: el historial no coincide con las 13 migraciones originales';
    END IF;
END
$$;

UPDATE public.flyway_schema_history h
SET version = m.new_version, script = 'V' || m.new_version || '__' || m.filename
FROM bug13_versions m
WHERE h.version = m.old_version AND m.old_version <> m.new_version;

COMMIT;
