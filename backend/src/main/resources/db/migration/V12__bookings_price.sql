-- BUG-10: la reserva guarda el precio con el que se hizo. Es lo que se cobra y lo que usan los reportes de
-- ingresos, aunque el proveedor cambie después el precio de la mentoría (HU-19).

ALTER TABLE bookings ADD COLUMN price NUMERIC(12, 2);

-- Reservas que ya existían: si tienen un pago PAID, ese monto es lo que de verdad se cobró (puede ser distinto del
-- precio actual si el proveedor lo cambió después). Si no se pagaron, se usa el precio actual de la mentoría.
-- BUG-09 garantiza que hay como máximo un pago PAID por reserva.
UPDATE bookings b
SET price = p.amount
FROM payments p
WHERE p.booking_id = b.id
  AND p.status = 'PAID';

UPDATE bookings b
SET price = o.price
FROM offerings o
WHERE o.id = b.offering_id
  AND b.price IS NULL;

ALTER TABLE bookings ALTER COLUMN price SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_price CHECK (price >= 0);
