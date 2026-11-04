"""EJEMPLO para copiar: cuántas reservas hay en cada estado.

Es la métrica más sencilla posible (un solo groupBy). Úsala como modelo para tu HU:
  1. Una función que recibe DataFrames y devuelve un DataFrame.
  2. Sin leer archivos ni crear la SparkSession aquí: eso lo hacen las pruebas y el pipeline.
  3. Columnas de salida con nombres claros, en snake_case.
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def reservas_por_estado(bookings: DataFrame) -> DataFrame:
    """Devuelve una fila por estado con su número de reservas.

    Entrada:  la tabla `bookings` del contrato (ver schemas.BOOKINGS).
    Salida:   columnas `status` y `reservas`, ordenadas por estado.
    """
    return (
        bookings
        .groupBy("status")                        # agrupa las filas que tienen el mismo estado
        .agg(F.count("*").alias("reservas"))      # cuenta cuántas filas hay en cada grupo
        .orderBy("status")                        # orden fijo: los resultados son reproducibles
    )
