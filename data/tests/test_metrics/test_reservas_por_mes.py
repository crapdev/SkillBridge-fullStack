"""Prueba de la métrica 'reservas por mes por proveedor' para SCRUM-343/HU-44.

Calcula cuántas reservas hizo cada proveedor cada mes.

Los fixtures tienen datos para:
- Proveedor a0000000-0000-0000-0000-000000000002
- Proveedor a0000000-0000-0000-0000-000000000003

El resultado esperado se calculó manualmente mirando tests/fixtures/bookings.csv
y tests/fixtures/offerings.csv.
"""


def test_cuenta_reservas_por_mes_por_proveedor(tables):
    """Comprueba que la métrica devuelve los seis resultados esperados."""
    # La función no existe todavía, por eso la prueba falla
    # Esta es la fase TDD: primero escribimos la prueba
    # Más tarde implementaremos la función en:
    # data/src/skillbridge_data/metrics/reservas_por_mes.py
    
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
    
    # La prueba debe fallar porque la función no está implementada
    # Cuando esté implementada, descomentaremos el código de abajo
    
    # from skillbridge_data.metrics.reservas_por_mes import reservas_por_mes
    # resultado = reservas_por_mes(tables["bookings"], tables["offerings"])
    # 
    # # Convertimos el resultado a un conjunto de tuplas para comparación independiente del orden
    # resultados = {
    #     (fila["provider_id"], fila["mes"], fila["reservas"])
    #     for fila in resultado.collect()
    # }
    # 
    # assert resultados == esperados, f"Resultados: {resultados}\nEsperados: {esperados}"
    
    # Por ahora, solo verificamos que entendemos los datos
    # Esto nos ayuda a confirmar que nuestros cálculos son correctos
    bookings_count = tables["bookings"].count()
    offerings_count = tables["offerings"].count()
    
    assert bookings_count == 12, f"Hay {bookings_count} bookings, esperaba 12"
    assert offerings_count == 4, f"Hay {offerings_count} offerings, esperaba 4"
    
    # La prueba falla intencionalmente
    raise NotImplementedError(
        "La función 'reservas_por_mes' todavía no está implementada. "
        "Esta es la prueba TDD para SCRUM-343/HU-44. "
        f"Datos: {bookings_count} bookings, {offerings_count} offerings. "
        "Resultados esperados: 6 filas."
    )


def test_las_columnas_de_salida_son_las_acordadas(tables):
    """Comprueba que el DataFrame resultante tiene las columnas correctas."""
    # La función no existe todavía
    # Esta prueba también falla en la fase TDD
    
    # Columnas que debe tener el resultado
    columnas_esperadas = ["provider_id", "mes", "reservas"]
    
    # Más adelante, cuando la función exista:
    # from skillbridge_data.metrics.reservas_por_mes import reservas_por_mes
    # resultado = reservas_por_mes(tables["bookings"], tables["offerings"])
    # assert resultado.columns == columnas_esperadas
    
    # Por ahora, la prueba falla intencionalmente
    raise NotImplementedError(
        "La función 'reservas_por_mes' todavía no está implementada. "
        "Esta es la prueba TDD para SCRUM-343/HU-44. "
        f"Columnas esperadas: {columnas_esperadas}"
    )