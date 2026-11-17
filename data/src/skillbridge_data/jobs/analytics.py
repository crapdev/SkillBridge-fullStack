"""Job de publicación de analítica para el panel de administración (SCRUM-415).

Carga las tablas del contrato, ejecuta las métricas y reglas de calidad en PySpark
(`reservas_por_estado`, `tasa_cancelacion` y `filas_con_nulos`), enriquece la tasa de
cancelación con `provider_name` uniendo con `users` (sin modificar `tasa_cancelacion`),
y escribe los archivos JSON con el contrato `SparkAnalytics` esperado por Angular:

  * data/output/analytics-fixtures.json
  * data/output/analytics-snapshot.json

Uso:
  python -m skillbridge_data.jobs.analytics
  python -m skillbridge_data.jobs.analytics --dataset fixtures
  python -m skillbridge_data.jobs.analytics --dataset snapshot --snapshot-dir /ruta/csv
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
from typing import Any, Literal

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

from skillbridge_data.metrics.ejemplo_reservas_por_estado import reservas_por_estado
from skillbridge_data.metrics.tasa_cancelacion import tasa_cancelacion
from skillbridge_data.quality.ejemplo_completitud import filas_con_nulos
from skillbridge_data.readers import read_all
from skillbridge_data.schemas import TABLES
from skillbridge_data.session import build_spark

DatasetName = Literal["fixtures", "snapshot"]
VALID_DATASETS: tuple[DatasetName, ...] = ("fixtures", "snapshot")

# data/ (cuatro niveles arriba desde data/src/skillbridge_data/jobs/analytics.py)
DATA_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_FIXTURES_DIR = DATA_ROOT / "tests" / "fixtures"
DEFAULT_SNAPSHOT_DIR = DATA_ROOT / "snapshots"
DEFAULT_OUTPUT_DIR = DATA_ROOT / "output"

# Métricas pendientes del backlog, respetando UpcomingMetric en admin-analytics.model.ts
PROXIMAS_METRICAS: list[dict[str, str]] = [
    {
        "title": "Calificación promedio por mentoría",
        "description": "Promedio de reseñas de 1 a 5 por mentoría y por proveedor.",
        "reference": "feature/data-calificacion-promedio",
        "technique": "join + groupBy + avg",
        "state": "EN_DESARROLLO",
    },
    {
        "title": "Conciliación de pagos vs. reservas",
        "description": "Pagos PAID sin reserva confirmada o completada, y al revés.",
        "reference": "HU-50",
        "technique": "full outer join",
        "state": "BACKLOG",
    },
    {
        "title": "Ocupación real de franjas",
        "description": "Franjas reservadas sobre franjas publicadas, sin contar las liberadas por cancelación.",
        "reference": "Backlog de datos",
        "technique": "join + agregación condicional",
        "state": "BACKLOG",
    },
    {
        "title": "Retención de clientes por mentor",
        "description": "Clientes que vuelven a reservar con el mismo proveedor.",
        "reference": "Backlog de datos",
        "technique": "window functions",
        "state": "BACKLOG",
    },
]


def enriquecer_tasa_con_proveedor(tasa_df: DataFrame, users: DataFrame) -> DataFrame:
    """Agrega `provider_name` a la salida de `tasa_cancelacion` uniendo con `users`.

    No altera `tasa_cancelacion.py` (sus columnas están fijadas por sus pruebas).
    Como el contrato de `users` excluye datos personales (`name`, `email`, `password`),
    cuando la tabla no trae columna `name` se genera un alias anónimo estable a partir
    del identificador del usuario (`prov_02`, `prov_03`, ...). Si el snapshot incluye
    `name`, se prioriza ese valor.
    """
    alias_anonimo = F.concat(F.lit("prov_"), F.substring(F.col("provider_id"), -2, 2))

    if "name" in users.columns:
        users_ref = users.select(
            F.col("id").alias("provider_id"),
            F.col("name").alias("_user_name"),
        )
        return (
            tasa_df.join(users_ref, on="provider_id", how="left")
            .withColumn("provider_name", F.coalesce(F.col("_user_name"), alias_anonimo))
            .select("provider_id", "provider_name", "reservas", "canceladas", "tasa_cancelacion")
            .orderBy("provider_id")
        )

    users_ref = users.select(F.col("id").alias("provider_id"))
    return (
        tasa_df.join(users_ref, on="provider_id", how="left")
        .withColumn("provider_name", alias_anonimo)
        .select("provider_id", "provider_name", "reservas", "canceladas", "tasa_cancelacion")
        .orderBy("provider_id")
    )


def calcular_completitud(tables: dict[str, DataFrame]) -> list[dict[str, Any]]:
    """Ejecuta `filas_con_nulos` sobre las columnas obligatorias de cada tabla del contrato."""
    resultado: list[dict[str, Any]] = []
    for tabla, schema in TABLES.items():
        df = tables[tabla]
        columnas_obligatorias = [field.name for field in schema.fields if not field.nullable]
        total_filas = int(df.count())
        nulos = int(filas_con_nulos(df, columnas_obligatorias).count()) if columnas_obligatorias else 0
        resultado.append({
            "tabla": tabla,
            "filas": total_filas,
            "columnas": columnas_obligatorias,
            "filas_con_nulos": nulos,
        })
    return resultado


def build_analytics_payload(
    spark: SparkSession,
    tables: dict[str, DataFrame],
    dataset: DatasetName,
    source: str,
) -> dict[str, Any]:
    """Construye el diccionario JSON con el contrato exacto `SparkAnalytics` del frontend."""
    if dataset not in VALID_DATASETS:
        raise ValueError(f"Dataset inválido: {dataset!r}. Valores permitidos: {VALID_DATASETS}")

    df_estados = reservas_por_estado(tables["bookings"])
    reservas_por_estado_rows = [
        {
            "status": str(row["status"]),
            "reservas": int(row["reservas"]),
        }
        for row in df_estados.collect()
    ]

    df_tasa = tasa_cancelacion(tables["bookings"], tables["offerings"])
    df_tasa_enriquecida = enriquecer_tasa_con_proveedor(df_tasa, tables["users"])
    tasa_cancelacion_rows = [
        {
            "provider_id": str(row["provider_id"]),
            "provider_name": str(row["provider_name"]),
            "reservas": int(row["reservas"]),
            "canceladas": int(row["canceladas"]),
            "tasa_cancelacion": float(row["tasa_cancelacion"]),
        }
        for row in df_tasa_enriquecida.collect()
    ]

    completitud_rows = calcular_completitud(tables)

    return {
        "dataset": dataset,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sparkVersion": spark.version,
        "source": source,
        "reservasPorEstado": reservas_por_estado_rows,
        "tasaCancelacion": tasa_cancelacion_rows,
        "completitud": completitud_rows,
        "proximas": PROXIMAS_METRICAS,
    }


def publish_analytics(
    spark: SparkSession,
    input_dir: str | Path,
    output_dir: str | Path,
    dataset: DatasetName,
    source: str | None = None,
) -> Path:
    """Lee las tablas desde `input_dir`, calcula métricas y escribe `analytics-<dataset>.json`."""
    input_path = Path(input_dir)
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    tables = read_all(spark, input_path)
    source_label = source or f"{input_path.as_posix()}/*.csv"
    payload = build_analytics_payload(spark, tables, dataset=dataset, source=source_label)

    target_file = output_path / f"analytics-{dataset}.json"
    target_file.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return target_file


def _resolve_snapshot_source(
    snapshot_dir: Path | None,
    fixtures_dir: Path,
) -> tuple[Path, str]:
    """Determina el directorio de entrada para la variante `snapshot`.

    Si se pasó `--snapshot-dir` explícitamente, exige que exista. En caso contrario, busca
    CSVs exportados en `ANALYTICS_SNAPSHOT_DIR` o `data/snapshots/`; si aún no existe un
    snapshot exportado de PostgreSQL, usa `fixtures_dir` como respaldo para que el pipeline
    siempre produzca un JSON válido en desarrollo local.
    """
    if snapshot_dir is not None:
        if not snapshot_dir.exists():
            raise FileNotFoundError(f"No existe el directorio de snapshot: {snapshot_dir}")
        return snapshot_dir, f"Snapshot de PostgreSQL ({snapshot_dir.as_posix()}/*.csv)"

    env_dir = os.getenv("ANALYTICS_SNAPSHOT_DIR")
    candidate = Path(env_dir) if env_dir else DEFAULT_SNAPSHOT_DIR
    if candidate.exists() and (candidate / "bookings.csv").exists():
        return candidate, f"Snapshot de PostgreSQL ({candidate.as_posix()}/*.csv)"

    return fixtures_dir, "data/tests/fixtures/*.csv (respaldo local sin snapshot exportado)"


def run_job(
    dataset: Literal["all", "fixtures", "snapshot"] = "all",
    fixtures_dir: Path | None = None,
    snapshot_dir: Path | None = None,
    output_dir: Path | None = None,
) -> list[Path]:
    """Ejecuta el job completo de analítica y devuelve las rutas de los JSON generados."""
    resolved_fixtures = fixtures_dir or DEFAULT_FIXTURES_DIR
    resolved_output = output_dir or DEFAULT_OUTPUT_DIR

    spark = build_spark("skillbridge-analytics-job")
    generated: list[Path] = []
    try:
        if dataset in ("all", "fixtures"):
            generated.append(
                publish_analytics(
                    spark=spark,
                    input_dir=resolved_fixtures,
                    output_dir=resolved_output,
                    dataset="fixtures",
                    source="data/tests/fixtures/*.csv",
                )
            )
        if dataset in ("all", "snapshot"):
            snap_path, snap_source = _resolve_snapshot_source(snapshot_dir, resolved_fixtures)
            generated.append(
                publish_analytics(
                    spark=spark,
                    input_dir=snap_path,
                    output_dir=resolved_output,
                    dataset="snapshot",
                    source=snap_source,
                )
            )
    finally:
        spark.stop()
    return generated


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Publica los resultados de analítica de PySpark en JSON para el panel admin."
    )
    parser.add_argument(
        "--dataset",
        choices=("all", "fixtures", "snapshot"),
        default="all",
        help="Variante a generar (por defecto: all, genera fixtures y snapshot).",
    )
    parser.add_argument(
        "--fixtures-dir",
        type=Path,
        default=None,
        help="Directorio con los CSV de fixtures (por defecto: data/tests/fixtures).",
    )
    parser.add_argument(
        "--snapshot-dir",
        type=Path,
        default=None,
        help="Directorio con el snapshot real exportado de PostgreSQL.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directorio de salida para los JSON (por defecto: data/output).",
    )
    args = parser.parse_args(argv)

    paths = run_job(
        dataset=args.dataset,
        fixtures_dir=args.fixtures_dir,
        snapshot_dir=args.snapshot_dir,
        output_dir=args.output_dir,
    )
    for path in paths:
        print(f"Generado: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

