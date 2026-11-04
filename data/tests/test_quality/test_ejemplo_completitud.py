"""EJEMPLO para copiar: prueba de una regla de calidad.

Cada regla necesita DOS pruebas:
  1. Con los datos correctos (fixtures) no encuentra nada  -> no hay falsos positivos.
  2. Con un defecto inyectado a propósito lo encuentra    -> la prueba de verdad sirve.
Los fixtures NUNCA se modifican: el defecto se inyecta sobre una copia en memoria.
"""
import pytest
from pyspark.sql import functions as F

from skillbridge_data.quality.ejemplo_completitud import filas_con_nulos

OBLIGATORIAS = ["id", "offering_id", "customer_id", "scheduled_at", "status"]


@pytest.mark.quality
def test_reservas_completas_en_datos_correctos(tables):
    assert filas_con_nulos(tables["bookings"], OBLIGATORIAS).count() == 0


@pytest.mark.quality
def test_detecta_reserva_sin_fecha(tables):
    # Defecto inyectado: la reserva ...0003 pierde su fecha
    con_defecto = tables["bookings"].withColumn(
        "scheduled_at",
        F.when(F.col("id").endswith("0003"), F.lit(None)).otherwise(F.col("scheduled_at")),
    )

    malas = filas_con_nulos(con_defecto, OBLIGATORIAS)

    assert malas.count() == 1
    assert malas.first()["id"].endswith("0003")       # y señala exactamente la fila dañada
