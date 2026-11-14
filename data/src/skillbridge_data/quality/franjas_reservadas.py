"""Regla de calidad: franjas marcadas como reservadas sin reserva activa (HU-45)."""
from pyspark.sql import functions as F


def franjas_sin_reserva(availability_slots, bookings):
  """Devuelve las franjas con reserved=True que no tienen una reserva activa asociada.

  Si los datos estan sanos, devuelve un DataFrame vacio (0 filas).
  """
  # 1. Filtramos solo las franjas que dicen estar ocupadas (reserved == True)
  franjas_ocupadas = availability_slots.where(F.col("reserved") == True)

  # 2. Filtramos solo las reservas reales que NO han sido canceladas
  reservas_activas = bookings.where(F.col("status") != "CANCELLED")

  # 3. Cruzamos por mentoria y fecha/hora usando left_anti:
  # Esto nos devuelve unicamente las franjas ocupadas que NO encontraron pareja
  # en la tabla de reservas activas.
  franjas_huerfanas = franjas_ocupadas.join(
    reservas_activas,
    on=["offering_id", "scheduled_at"],
    how="left_anti",
  )

  return franjas_huerfanas
