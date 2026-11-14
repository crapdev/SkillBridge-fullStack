"""Metrica de ocupacion de franjas por mentoria (HU-45)."""
from pyspark.sql import functions as F


def ocupacion_franjas(availability_slots):
  """Calcula el total de franjas, las reservadas y su porcentaje por mentoria.

  Entrada: tabla `availability_slots`.
  Salida: columnas `offering_id`, `franjas`, `reservadas`, `porcentaje_ocupacion`.
  """
  # 1. Agrupamos por mentoria y contamos el total de horarios y los ocupados.
  # Como `reserved` es booleano (true/false), lo pasamos a entero con cast("int")
  # para que true valga 1 y false valga 0, y asi poder sumarlos.
  agrupado = (
    availability_slots
    .groupBy("offering_id")
    .agg(
      F.count("*").alias("franjas"),
      F.sum(F.col("reserved").cast("int")).alias("reservadas"),
    )
  )

  # 2. Calculamos el porcentaje de ocupacion redondeado a 2 decimales
  # y ordenamos por offering_id para que el resultado siempre salga igual.
  resultado = (
    agrupado
    .withColumn(
      "porcentaje_ocupacion",
      F.round((F.col("reservadas") / F.col("franjas")) * 100, 2),
    )
    .orderBy("offering_id")
  )

  return resultado
