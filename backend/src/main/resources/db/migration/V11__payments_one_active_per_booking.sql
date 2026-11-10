-- BUG-09: una reserva solo puede tener un pago activo (PENDING o PAID).
-- Antes, cada vez que el cliente abría la pantalla de pago se guardaba un PENDING nuevo y los anteriores
-- quedaban abandonados. Los FAILED (rechazados) y CANCELED (reemplazados) no cuentan: el cliente puede reintentar.
-- Los estados deben coincidir con PaymentPersistenceAdapter.ACTIVE_STATUSES.

-- 1. Dos pagos PAID para la misma reserva son un cobro doble: alguien tiene que revisarlo (y reembolsar)
--    antes de seguir. La migración no elige cuál de los dos pagos "vale".
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM payments WHERE status = 'PAID' GROUP BY booking_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'BUG-09: hay reservas con más de un pago PAID (cobro doble). Revísalas antes de aplicar V11: '
            'SELECT booking_id, COUNT(*) FROM payments WHERE status = ''PAID'' GROUP BY booking_id HAVING COUNT(*) > 1;';
    END IF;
END $$;

-- 2. Se deja un pago activo por reserva y los demás PENDING pasan a CANCELED (no fueron rechazos, se abandonaron):
--    primero el PAID y, si no hay, el PENDING más reciente (el que el cliente abrió por última vez).
--    Antes de desplegar, revisa cuántos duplicados hay con:
--      SELECT booking_id, COUNT(*) FROM payments
--      WHERE status IN ('PENDING', 'PAID') GROUP BY booking_id HAVING COUNT(*) > 1;
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY booking_id
               ORDER BY CASE status WHEN 'PAID' THEN 0 ELSE 1 END,
                        created_at DESC,
                        id
           ) AS position
    FROM payments
    WHERE status IN ('PENDING', 'PAID')
)
UPDATE payments p
SET status = 'CANCELED'
FROM ranked r
WHERE p.id = r.id
  AND r.position > 1;

-- 3. Índice único parcial: solo entran los pagos activos
CREATE UNIQUE INDEX uq_payments_active_booking
    ON payments (booking_id)
    WHERE status IN ('PENDING', 'PAID');
