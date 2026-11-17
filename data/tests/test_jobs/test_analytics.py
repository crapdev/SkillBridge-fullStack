"""Pruebas del job de publicación de analítica (SCRUM-415).

Verifica que:
1. El job genera el archivo JSON para `fixtures` respetando el contrato `SparkAnalytics` del frontend.
2. Con fixtures, la tasa de cancelación de a...02 es 12.5% (prov_02) y la de a...03 es 25.0% (prov_03).
3. El join con `users` ocurre en el job sin alterar las columnas de `tasa_cancelacion`.
4. También genera correctamente la variante `snapshot` (`analytics-snapshot.json`).
"""
import json
from datetime import datetime

from pyspark.sql import functions as F

from skillbridge_data.jobs.analytics import (
    enriquecer_tasa_con_proveedor,
    publish_analytics,
)
from skillbridge_data.metrics.tasa_cancelacion import tasa_cancelacion
from skillbridge_data.schemas import BOOKING_STATUSES, TABLES

SPARK_ANALYTICS_KEYS = {
    "dataset",
    "generatedAt",
    "sparkVersion",
    "source",
    "reservasPorEstado",
    "tasaCancelacion",
    "completitud",
    "proximas",
}


def test_job_genera_json_fixtures_con_contrato_frontend(spark, fixtures_dir, tmp_path):
    output_file = publish_analytics(
        spark=spark,
        input_dir=fixtures_dir,
        output_dir=tmp_path,
        dataset="fixtures",
        source="data/tests/fixtures/*.csv",
    )

    assert output_file == tmp_path / "analytics-fixtures.json"
    assert output_file.exists()

    payload = json.loads(output_file.read_text(encoding="utf-8"))

    # 1. Contrato raíz SparkAnalytics
    assert set(payload.keys()) == SPARK_ANALYTICS_KEYS
    assert payload["dataset"] == "fixtures"
    assert payload["sparkVersion"] == spark.version
    assert payload["source"] == "data/tests/fixtures/*.csv"
    assert payload["generatedAt"].endswith("Z")
    # Verifica que generatedAt sea ISO-8601 UTC válido
    datetime.fromisoformat(payload["generatedAt"].replace("Z", "+00:00"))

    # 2. reservasPorEstado (contrato ReservasPorEstadoRow)
    estados = {row["status"]: row["reservas"] for row in payload["reservasPorEstado"]}
    for row in payload["reservasPorEstado"]:
        assert set(row.keys()) == {"status", "reservas"}
        assert row["status"] in BOOKING_STATUSES
        assert isinstance(row["reservas"], int)
    assert estados == {
        "CANCELLED": 2,
        "COMPLETED": 6,
        "CONFIRMED": 2,
        "CREATED": 2,
    }

    # 3. tasaCancelacion (contrato TasaCancelacionRow: a...02 = 12.5, a...03 = 25.0)
    assert len(payload["tasaCancelacion"]) == 2
    for row in payload["tasaCancelacion"]:
        assert set(row.keys()) == {
            "provider_id",
            "provider_name",
            "reservas",
            "canceladas",
            "tasa_cancelacion",
        }

    por_proveedor = {row["provider_id"]: row for row in payload["tasaCancelacion"]}

    prov_02 = por_proveedor["a0000000-0000-0000-0000-000000000002"]
    assert prov_02["provider_name"] == "prov_02"
    assert prov_02["reservas"] == 8
    assert prov_02["canceladas"] == 1
    assert prov_02["tasa_cancelacion"] == 12.5

    prov_03 = por_proveedor["a0000000-0000-0000-0000-000000000003"]
    assert prov_03["provider_name"] == "prov_03"
    assert prov_03["reservas"] == 4
    assert prov_03["canceladas"] == 1
    assert prov_03["tasa_cancelacion"] == 25.0

    # 4. completitud (contrato CompletitudRow para las 6 tablas)
    assert len(payload["completitud"]) == len(TABLES)
    completitud_por_tabla = {row["tabla"]: row for row in payload["completitud"]}
    assert set(completitud_por_tabla.keys()) == set(TABLES.keys())
    for tabla, row in completitud_por_tabla.items():
        assert set(row.keys()) == {"tabla", "filas", "columnas", "filas_con_nulos"}
        assert row["filas_con_nulos"] == 0
        expected_cols = [f.name for f in TABLES[tabla].fields if not f.nullable]
        assert row["columnas"] == expected_cols

    assert completitud_por_tabla["bookings"]["filas"] == 12
    assert completitud_por_tabla["users"]["filas"] == 7

    # 5. proximas (contrato UpcomingMetric)
    assert len(payload["proximas"]) > 0
    for item in payload["proximas"]:
        assert set(item.keys()) == {"title", "description", "reference", "technique", "state"}
        assert item["state"] in {"EN_DESARROLLO", "BACKLOG"}


def test_job_genera_variante_snapshot_y_respeta_tasa_cancelacion(spark, fixtures_dir, tables, tmp_path):
    # La función base tasa_cancelacion NO debe cambiar sus columnas
    tasa_base = tasa_cancelacion(tables["bookings"], tables["offerings"])
    assert tasa_base.columns == ["provider_id", "reservas", "canceladas", "tasa_cancelacion"]

    # Si users trae columna `name`, el join del job la usa para provider_name
    users_con_nombre = tables["users"].withColumn(
        "name",
        F.when(F.col("id").endswith("02"), F.lit("Ana Pérez"))
        .when(F.col("id").endswith("03"), F.lit("Carlos Morales")),
    )
    enriquecida = enriquecer_tasa_con_proveedor(tasa_base, users_con_nombre)
    nombres = {row["provider_id"]: row["provider_name"] for row in enriquecida.collect()}
    assert nombres["a0000000-0000-0000-0000-000000000002"] == "Ana Pérez"
    assert nombres["a0000000-0000-0000-0000-000000000003"] == "Carlos Morales"

    # Publicación de la variante `snapshot`
    output_file = publish_analytics(
        spark=spark,
        input_dir=fixtures_dir,
        output_dir=tmp_path,
        dataset="snapshot",
        source="Snapshot de PostgreSQL (prueba)",
    )
    assert output_file == tmp_path / "analytics-snapshot.json"
    payload = json.loads(output_file.read_text(encoding="utf-8"))
    assert payload["dataset"] == "snapshot"
    assert set(payload.keys()) == SPARK_ANALYTICS_KEYS

