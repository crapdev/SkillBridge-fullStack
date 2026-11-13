"""Reglas de calidad: IDs únicos y estados válidos para SCRUM-345/HU-44.

Detecta:
- Reservas con identificadores duplicados.
- Reservas con estados que no pertenecen al dominio permitido.

Patrón de todas las reglas de calidad:
  * La función devuelve las filas que INCUMPLEN la regla.
  * Si devuelve un DataFrame vacío (count() == 0), los datos están bien.
  * Así, cuando la regla falla, el DataFrame dice exactamente qué filas revisar.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from skillbridge_data.schemas import BOOKING_STATUSES


def ids_duplicados(df: DataFrame) -> DataFrame:
    """Identifica reservas cuyo ID aparece más de una vez.

    Parámetros:
        df: DataFrame de reservas (debe tener columna 'id')
    
    Retorna:
        DataFrame con columnas 'id' y 'conteo' para los IDs duplicados.
    """
    # Agrupar por id y contar cuántas veces aparece cada uno
    conteos = (
        df
        .groupBy("id")
        .agg(F.count("*").alias("conteo"))
    )
    
    # Filtrar solo los IDs que aparecen más de una vez
    duplicados = conteos.where(F.col("conteo") > 1)
    
    return duplicados


def estados_invalidos(bookings: DataFrame) -> DataFrame:
    """Identifica reservas con estados no permitidos o nulos.

    Estados permitidos definidos en schemas.BOOKING_STATUSES:
        'CREATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'
    
    Parámetros:
        bookings: DataFrame de reservas (debe tener columna 'status')
    
    Retorna:
        DataFrame con las filas cuyo status no está en el dominio permitido o es nulo.
    """
    # Convertir la tupla a lista para que Spark la acepte en isin()
    estados_permitidos = list(BOOKING_STATUSES)
    
    # Filtrar filas donde:
    # 1. status no está en los estados permitidos, O
    # 2. status es nulo
    return bookings.where(
        (~F.col("status").isin(estados_permitidos)) | (F.col("status").isNull())
    )