"""Pruebas de calidad: integridad referencial de las reservas (HU-46 / SCRUM-353).

Demuestra que:
1. Con los fixtures limpios, cada regla devuelve 0 filas (datos sanos).
2. Con defectos inyectados en memoria, cada regla detecta la fila dañada y señala su ID exacto.
"""
import pytest
from pyspark.sql import functions as F

from skillbridge_data.quality.integridad_referencial import (
    reservas_sin_cliente,
    reservas_sin_offering,
)


@pytest.mark.quality
def test_reservas_apuntan_a_offerings_existentes(tables):
    """Prueba 1: Con datos sanos, ninguna reserva tiene offering inexistente (0 filas)."""
    assert reservas_sin_offering(tables["bookings"], tables["offerings"]).count() == 0


@pytest.mark.quality
def test_reservas_apuntan_a_clientes_existentes(tables):
    """Prueba 2: Con datos sanos, ninguna reserva tiene cliente inexistente (0 filas)."""
    assert reservas_sin_cliente(tables["bookings"], tables["users"]).count() == 0


@pytest.mark.quality
def test_detecta_reserva_con_offering_inexistente(tables):
    """Prueba 3: Detecta una reserva inyectada con un offering_id que no existe."""
    # Creamos en memoria una fila dañada a partir de una existente
    fila_mala = (
        tables["bookings"]
        .where(F.col("id").endswith("0001"))
        .withColumn("id", F.lit("c-defecto"))
        .withColumn("offering_id", F.lit("b-no-existe"))
    )
    con_defecto = tables["bookings"].union(fila_mala)

    malas = reservas_sin_offering(con_defecto, tables["offerings"])

    assert malas.count() == 1
    assert malas.first()["id"] == "c-defecto"


@pytest.mark.quality
def test_detecta_reserva_con_cliente_inexistente(tables):
    """Prueba 4: Detecta una reserva inyectada con un customer_id que no existe."""
    # Creamos en memoria una fila dañada a partir de una existente
    fila_mala = (
        tables["bookings"]
        .where(F.col("id").endswith("0001"))
        .withColumn("id", F.lit("c-defecto"))
        .withColumn("customer_id", F.lit("a-no-existe"))
    )
    con_defecto = tables["bookings"].union(fila_mala)

    malas = reservas_sin_cliente(con_defecto, tables["users"])

    assert malas.count() == 1
    assert malas.first()["id"] == "c-defecto"