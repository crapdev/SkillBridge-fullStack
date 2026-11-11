"""Prueba de la métrica: tasa de cancelación por proveedor (HU-46 / SCRUM-351).

`tables` viene de tests/conftest.py: es un diccionario con las tablas de tests/fixtures ya cargadas.
El resultado esperado se calculó A MANO mirando tests/fixtures/bookings.csv y offerings.csv.
"""
from skillbridge_data.metrics.tasa_cancelacion import tasa_cancelacion


def test_tasa_cancelacion_por_proveedor(tables):
    resultado = tasa_cancelacion(tables["bookings"], tables["offerings"])

    # collect() trae las filas de Spark a Python; cada fila se usa como un diccionario.
    # Creamos un diccionario donde la clave es el provider_id y el valor es la fila completa.
    por_proveedor = {fila["provider_id"]: fila for fila in resultado.collect()}

    # Proveedor a...02: 8 reservas totales, 1 cancelada → 12.5%
    prov_02 = por_proveedor["a0000000-0000-0000-0000-000000000002"]
    assert prov_02["reservas"] == 8
    assert prov_02["canceladas"] == 1
    assert float(prov_02["tasa_cancelacion"]) == 12.5

    # Proveedor a...03: 4 reservas totales, 1 cancelada → 25.0%
    prov_03 = por_proveedor["a0000000-0000-0000-0000-000000000003"]
    assert prov_03["reservas"] == 4
    assert prov_03["canceladas"] == 1
    assert float(prov_03["tasa_cancelacion"]) == 25.0


def test_las_columnas_de_salida_son_las_acordadas(tables):
    resultado = tasa_cancelacion(tables["bookings"], tables["offerings"])
    assert resultado.columns == ["provider_id", "reservas", "canceladas", "tasa_cancelacion"]

