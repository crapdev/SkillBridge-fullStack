"""Pruebas de las reglas de calidad: IDs únicos y estados válidos para SCRUM-345/HU-44.

Cada regla necesita DOS pruebas:
  1. Con los datos correctos (fixtures) no encuentra nada  -> no hay falsos positivos.
  2. Con un defecto inyectado a propósito lo encuentra    -> la prueba de verdad sirve.
Los fixtures NUNCA se modifican: el defecto se inyecta sobre una copia en memoria.
"""
import pytest
from pyspark.sql import functions as F

from skillbridge_data.quality.unicidad_y_dominio import ids_duplicados, estados_invalidos


@pytest.mark.quality
def test_no_hay_ids_duplicados_en_datos_correctos(tables):
    """Con los fixtures originales, ids_duplicados devuelve cero filas."""
    duplicados = ids_duplicados(tables["bookings"])
    assert duplicados.count() == 0


@pytest.mark.quality
def test_no_hay_estados_invalidos_en_datos_correctos(tables):
    """Con los fixtures originales, estados_invalidos devuelve cero filas."""
    invalidos = estados_invalidos(tables["bookings"])
    assert invalidos.count() == 0


@pytest.mark.quality
def test_detecta_id_duplicado(tables):
    """Comprobar que ids_duplicados detecta un identificador duplicado inyectado."""
    # Crear un defecto en memoria: duplicar la reserva con id ...0001
    # Mantenemos el mismo ID para crear un duplicado real
    reserva_duplicada = (
        tables["bookings"]
        .where(F.col("id").endswith("0001"))
    )
    
    # Unir la copia duplicada con las reservas originales
    # Ahora hay dos filas con el mismo id c0000000-0000-0000-0000-000000000001
    con_defecto = tables["bookings"].union(reserva_duplicada)
    
    # Ejecutar la regla
    duplicados = ids_duplicados(con_defecto)
    
    # Verificar que detecta el duplicado
    assert duplicados.count() == 1
    assert duplicados.first()["id"] == "c0000000-0000-0000-0000-000000000001"
    assert duplicados.first()["conteo"] == 2


@pytest.mark.quality
def test_detecta_estado_invalido(tables):
    """Comprobar que estados_invalidos detecta un estado no permitido inyectado."""
    # Crear un defecto en memoria: cambiar el estado de la reserva ...0002 a "PAGADA"
    con_defecto = tables["bookings"].withColumn(
        "status",
        F.when(
            F.col("id").endswith("0002"),
            F.lit("PAGADA")  # Estado no permitido (no está en BOOKING_STATUSES)
        ).otherwise(F.col("status"))
    )
    
    # Ejecutar la regla
    invalidos = estados_invalidos(con_defecto)
    
    # Verificar que detecta la fila dañada
    assert invalidos.count() == 1
    assert invalidos.first()["id"].endswith("0002")
    assert invalidos.first()["status"] == "PAGADA"