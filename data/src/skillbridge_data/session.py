"""SparkSession compartida por los jobs y las pruebas."""
import os

from pyspark.sql import SparkSession


def build_spark(app_name: str = "skillbridge-data") -> SparkSession:
    """SparkSession en modo local, con la zona horaria fija en UTC.

    La memoria del driver se ajusta con SPARK_DRIVER_MEMORY (por defecto 1g) para equipos modestos.
    """
    return (
        SparkSession.builder
        .appName(app_name)
        .master(os.getenv("SPARK_MASTER", "local[2]"))
        .config("spark.driver.memory", os.getenv("SPARK_DRIVER_MEMORY", "1g"))
        .config("spark.sql.session.timeZone", "UTC")      # las fechas se interpretan y muestran en UTC
        .config("spark.sql.shuffle.partitions", os.getenv("SPARK_SHUFFLE_PARTITIONS", "4"))
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    )
