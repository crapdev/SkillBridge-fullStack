"""Fixtures compartidas por todas las pruebas de datos."""
import os
from pathlib import Path

import pytest

from skillbridge_data.readers import read_all
from skillbridge_data.session import build_spark

FIXTURES_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture(scope="session")
def spark():
    """Una sola SparkSession para toda la corrida: arrancar la JVM tarda varios segundos."""
    session = build_spark("skillbridge-data-tests")
    yield session
    session.stop()


@pytest.fixture(scope="session")
def fixtures_dir() -> Path:
    return FIXTURES_DIR


@pytest.fixture(scope="session")
def tables(spark, fixtures_dir):
    """Datos pequeños y correctos de tests/fixtures, por nombre de tabla.

    Son la base sin defectos: una prueba de calidad debe pasar con ellos y fallar
    cuando se les inyecta un defecto. No se modifican; cada prueba deriva sus propios DataFrames.
    """
    return {name: df.cache() for name, df in read_all(spark, fixtures_dir).items()}


@pytest.fixture
def parquet_dir(tmp_path):
    """Carpeta temporal para escribir Parquet en una prueba.

    En Windows sin Docker, Spark no puede escribir Parquet (pide HADOOP_HOME y winutils.exe):
    la prueba se omite con un aviso y se ejecuta en Docker (make data-test) y en el CI.
    """
    if os.name == "nt" and not os.getenv("HADOOP_HOME"):
        pytest.skip("Escribir Parquet en Windows requiere Docker: corre `make data-test`")
    return tmp_path / "parquet"
