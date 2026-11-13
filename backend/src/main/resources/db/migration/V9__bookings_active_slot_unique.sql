-- BUG-08: una franja (mentoría + hora) solo puede tener una reserva activa.
-- La base de datos es la que garantiza que, si dos clientes reservan al mismo tiempo, solo uno gana.
-- Las reservas CANCELLED no cuentan, igual que en BookingPersistenceAdapter.RELEASED_STATUSES (BUG-07).

-- 1. El índice no se puede crear si ya hay duplicados. Se deja una reserva por franja y las demás pasan a CANCELLED:
--    primero la COMPLETED, después la CONFIRMED (ya pagada) y, si empatan, la que se creó primero.
--    Antes de desplegar, revisa si la base tiene duplicados con:
--      SELECT offering_id, scheduled_at, COUNT(*) FROM bookings
--      WHERE status <> 'CANCELLED' GROUP BY offering_id, scheduled_at HAVING COUNT(*) > 1;
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY offering_id, scheduled_at
               ORDER BY CASE status WHEN 'COMPLETED' THEN 0 WHEN 'CONFIRMED' THEN 1 ELSE 2 END,
                        created_at,
                        id
           ) AS position
    FROM bookings
    WHERE status <> 'CANCELLED'
)
UPDATE bookings b
SET status = 'CANCELLED'
FROM ranked r
WHERE b.id = r.id
  AND r.position > 1;

-- 2. Índice único parcial: solo entran las reservas activas
CREATE UNIQUE INDEX uq_bookings_active_slot
    ON bookings (offering_id, scheduled_at)
    WHERE status <> 'CANCELLED';
