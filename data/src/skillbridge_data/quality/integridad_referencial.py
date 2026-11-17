"""Reglas de calidad: integridad referencial de las reservas (HU-46 / SCRUM-353).

Detecta reservas huérfanas que apuntan a mentorías (offerings) o clientes (users) inexistentes.
Sigue el patrón de calidad: devuelve las filas que INCUMPLEN la regla (0 filas = datos sanos).
"""
from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def reservas_sin_offering(bookings: DataFrame, offerings: DataFrame) -> DataFrame:
    """Devuelve las reservas cuyo offering_id no existe en la tabla offerings.

    Usa un join con how='left_anti': solo conserva las reservas que NO tienen
    coincidencia en offerings.
    """
    offerings_ids = offerings.select(F.col("id").alias("offering_id"))
    return bookings.join(offerings_ids, on="offering_id", how="left_anti")


def reservas_sin_cliente(bookings: DataFrame, users: DataFrame) -> DataFrame:
    """Devuelve las reservas cuyo customer_id no existe en la tabla users.

    Usa un join con how='left_anti': solo conserva las reservas que NO tienen
    coincidencia en users.
    """
    users_ids = users.select(F.col("id").alias("customer_id"))
    return bookings.join(users_ids, on="customer_id", how="left_anti")
