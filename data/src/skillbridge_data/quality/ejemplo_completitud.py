"""EJEMPLO para copiar: regla de completitud (campos obligatorios sin nulos).

Patrón de todas las reglas de calidad:
  * La función devuelve las filas que INCUMPLEN la regla.
  * Si devuelve un DataFrame vacío (count() == 0), los datos están bien.
  * Así, cuando la regla falla, el DataFrame dice exactamente qué filas revisar.
"""
from functools import reduce

from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def filas_con_nulos(df: DataFrame, columnas: list[str]) -> DataFrame:
    """Filas en las que alguna de `columnas` está vacía (null)."""
    condiciones = [F.col(c).isNull() for c in columnas]          # una condición por columna
    alguna_nula = reduce(lambda a, b: a | b, condiciones)        # c1 nula OR c2 nula OR ...
    return df.where(alguna_nula)
