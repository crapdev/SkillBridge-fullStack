"""Métrica 'reservas por mes por proveedor' para SCRUM-344/HU-44.

Calcula cuántas reservas hizo cada proveedor cada mes.

Todas las reservas se cuentan, independientemente de su estado.
El mes se extrae en UTC con formato yyyy-MM.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def reservas_por_mes(bookings: DataFrame, offerings: DataFrame) -> DataFrame:
    """Devuelve una fila por proveedor y mes con su número de reservas.

    Entrada:
      - bookings: tabla de reservas con scheduled_at (timestamp en UTC)
      - offerings: tabla de ofertas con provider_id
    Salida:
      - provider_id: identificador del proveedor
      - mes: año y mes en formato yyyy-MM (UTC)
      - reservas: número total de reservas en ese mes para ese proveedor
      Ordenado por provider_id, mes.
    """
    # Paso A: Preparar offerings, seleccionando solo las columnas necesarias
    # y renombrando id a offering_id para evitar ambigüedades en el JOIN
    offerings_preparadas = offerings.select(
        F.col("id").alias("offering_id"),
        "provider_id"
    )
    
    # Paso B: Realizar el JOIN entre bookings y offerings
    # Usamos offering_id como columna común
    reservas_con_proveedor = bookings.join(offerings_preparadas, "offering_id")
    
    # Paso C: Crear la columna mes en formato yyyy-MM
    # Las fechas ya están en UTC según el contrato de datos (schemas.py)
    # F.date_format maneja timestamps en UTC correctamente
    reservas_con_mes = reservas_con_proveedor.withColumn(
        "mes",
        F.date_format("scheduled_at", "yyyy-MM")
    )
    
    # Paso D: Agrupar por provider_id y mes, contar todas las reservas
    # No filtramos por status - contamos todas las reservas
    resultado = (
        reservas_con_mes
        .groupBy("provider_id", "mes")
        .agg(F.count("*").alias("reservas"))
    )
    
    # Paso E: Ordenar el resultado para que sea reproducible
    resultado_ordenado = resultado.orderBy("provider_id", "mes")
    
    return resultado_ordenado