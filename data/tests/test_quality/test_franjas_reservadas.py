"""Pruebas para la regla de calidad de franjas reservadas (HU-45)."""
from pyspark.sql import functions as F
from skillbridge_data.quality.franjas_reservadas import franjas_sin_reserva


def test_fixtures_sin_franjas_huerfanas(tables):
  # Con los datos originales (sanos), la regla debe devolver 0 filas
  malas = franjas_sin_reserva(tables["availability_slots"], tables["bookings"])
  assert malas.count() == 0


def test_detecta_franja_marcada_reservada_sin_reserva(tables):
  # Inyectamos el defecto en memoria (SIN editar el archivo CSV):
  # Marcamos como reserved=True la franja libre cuyo id termina en "0013"
  slots_con_defecto = tables["availability_slots"].withColumn(
    "reserved",
    F.when(F.col("id").endswith("0013"), F.lit(True)).otherwise(F.col("reserved")),
  )

  # Ejecutamos la regla sobre los datos danados
  malas = franjas_sin_reserva(slots_con_defecto, tables["bookings"])

  # Comprobamos que atrapa exactamente 1 fila y que es la d...13
  assert malas.count() == 1
  assert malas.first()["id"].endswith("0013")
