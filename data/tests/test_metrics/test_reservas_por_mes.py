"""Prueba de la métrica 'reservas por mes por proveedor' para SCRUM-343/HU-44.

Calcula cuántas reservas hizo cada proveedor cada mes.

Los fixtures tienen datos para:
- Proveedor a0000000-0000-0000-0000-000000000002
- Proveedor a0000000-0000-0000-0000-000000000003

El resultado esperado se calculó manualmente mirando tests/fixtures/bookings.csv
y tests/fixtures/offerings.csv.
"""

from skillbridge_data.metrics.reservas_por_mes import reservas_por_mes


def test_cuenta_reservas_por_mes_por_proveedor(tables):
    """Comprueba que la métrica devuelve los seis resultados esperados."""
    
    # Resultados esperados (6 filas):
    # Proveedor a...02: 2026-08 (3), 2026-09 (2), 2026-10 (3)
    # Proveedor a...03: 2026-08 (1), 2026-09 (2), 2026-10 (1)
    esperados = {
        ("a0000000-0000-0000-0000-000000000002", "2026-08", 3),
        ("a0000000-0000-0000-0000-000000000002", "2026-09", 2),
        ("a0000000-0000-0000-0000-000000000002", "2026-10", 3),
        ("a0000000-0000-0000-0000-000000000003", "2026-08", 1),
        ("a0000000-0000-0000-0000-000000000003", "2026-09", 2),
        ("a0000000-0000-0000-0000-000000000003", "2026-10", 1),
    }
    
    resultado = reservas_por_mes(tables["bookings"], tables["offerings"])
    
    # Convertimos el resultado a un conjunto de tuplas para comparación independiente del orden
    resultados = {
        (fila["provider_id"], fila["mes"], fila["reservas"])
        for fila in resultado.collect()
    }
    
    assert resultados == esperados, f"Resultados: {resultados}\nEsperados: {esperados}"


def test_las_columnas_de_salida_son_las_acordadas(tables):
    """Comprueba que el DataFrame resultante tiene las columnas correctas."""
    # Columnas que debe tener el resultado
    columnas_esperadas = ["provider_id", "mes", "reservas"]
    
    resultado = reservas_por_mes(tables["bookings"], tables["offerings"])
    assert resultado.columns == columnas_esperadas