"""EJEMPLO para copiar: prueba de una métrica.

`tables` viene de tests/conftest.py: es un diccionario con las tablas de tests/fixtures ya cargadas.
El resultado esperado se calculó A MANO mirando tests/fixtures/bookings.csv.
"""
from skillbridge_data.metrics.ejemplo_reservas_por_estado import reservas_por_estado


def test_cuenta_reservas_por_estado(tables):
    resultado = reservas_por_estado(tables["bookings"])

    # collect() trae las filas de Spark a Python; cada fila se usa como un diccionario
    por_estado = {fila["status"]: fila["reservas"] for fila in resultado.collect()}

    assert por_estado == {
        "CANCELLED": 2,
        "COMPLETED": 6,
        "CONFIRMED": 2,
        "CREATED": 2,
    }


def test_las_columnas_de_salida_son_las_acordadas(tables):
    resultado = reservas_por_estado(tables["bookings"])
    assert resultado.columns == ["status", "reservas"]
