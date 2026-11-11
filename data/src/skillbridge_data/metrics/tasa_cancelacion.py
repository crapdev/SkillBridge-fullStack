"""Métrica: tasa de cancelación por proveedor (HU-46 / SCRUM-352).

Calcula el porcentaje de reservas canceladas sobre el total de reservas de cada proveedor.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def tasa_cancelacion(bookings: DataFrame, offerings: DataFrame) -> DataFrame:
    """Devuelve una fila por proveedor con sus reservas totales, canceladas y tasa de cancelación.

    Entrada:
      - bookings: tabla de reservas (con offering_id, status, etc.)
      - offerings: tabla de mentorías (con id, provider_id, etc.)

    Salida:
      - Columnas: provider_id, reservas, canceladas, tasa_cancelacion (ordenadas por provider_id).
    """
    # 1. De offerings tomamos solo id (renombrado a offering_id para el join) y provider_id
    offerings_clean = offerings.select(
        F.col("id").alias("offering_id"),
        "provider_id",
    )

    # 2. Unimos bookings con offerings por offering_id
    reservas_con_proveedor = bookings.join(offerings_clean, on="offering_id")

    # 3. Agrupamos por proveedor, contamos total y canceladas, calculamos tasa y ordenamos
    return (
        reservas_con_proveedor
        .groupBy("provider_id")
        .agg(
            F.count("*").alias("reservas"),
            F.sum((F.col("status") == "CANCELLED").cast("int")).alias("canceladas"),
        )
        .withColumn(
            "tasa_cancelacion",
            F.round((F.col("canceladas") / F.col("reservas")) * 100, 2),
        )
        .orderBy("provider_id")
    )

