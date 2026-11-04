"""Lectura de las tablas exportadas aplicando el contrato de schemas.py."""
from pathlib import Path

from pyspark.sql import DataFrame, SparkSession

from skillbridge_data.schemas import TABLES


def read_table(spark: SparkSession, name: str, base_dir: str | Path) -> DataFrame:
    """Lee <base_dir>/<name>.csv con su esquema explícito.

    Falla si el encabezado no coincide con el esquema o si un valor no se puede convertir
    (por ejemplo, una fecha escrita como texto): así un cambio de contrato no pasa en silencio.
    """
    schema = TABLES[name]
    return (
        spark.read
        .option("header", True)
        .option("enforceSchema", False)   # compara el encabezado con los nombres del esquema
        .option("mode", "FAILFAST")       # una fila mal formada detiene la lectura
        .schema(schema)
        .csv(str(Path(base_dir) / f"{name}.csv"))
    )


def read_all(spark: SparkSession, base_dir: str | Path) -> dict[str, DataFrame]:
    """Lee todas las tablas del contrato."""
    return {name: read_table(spark, name, base_dir) for name in TABLES}
