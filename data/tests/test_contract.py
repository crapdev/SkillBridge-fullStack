"""El contrato de schemas.py y los fixtures están de acuerdo.

Si alguien cambia un esquema sin actualizar los fixtures (o al revés), estas pruebas fallan primero.
"""
import pytest
from pyspark.sql import functions as F

from skillbridge_data.readers import read_table
from skillbridge_data.schemas import TABLES

EXPECTED_ROWS = {
    "users": 7,
    "offerings": 4,
    "bookings": 12,
    "availability_slots": 14,
    "payments": 10,
    "reviews": 6,
}


@pytest.mark.parametrize("name", sorted(TABLES))
def test_fixture_loads_with_contract_schema(tables, name):
    df = tables[name]
    assert [f.name for f in df.schema] == [f.name for f in TABLES[name]]
    assert [f.dataType for f in df.schema] == [f.dataType for f in TABLES[name]]
    assert df.count() == EXPECTED_ROWS[name]


@pytest.mark.parametrize("name", sorted(TABLES))
def test_fixture_ids_are_unique(tables, name):
    df = tables[name]
    assert df.select("id").distinct().count() == df.count()


def test_timestamps_are_read_in_utc(tables):
    # Se formatea dentro de Spark: collect() entrega datetimes sin zona en la hora local del equipo
    first = (
        tables["bookings"]
        .orderBy("scheduled_at")
        .select(F.date_format("scheduled_at", "yyyy-MM-dd'T'HH:mm:ssXXX").alias("utc"))
        .first()
    )
    assert first["utc"] == "2026-08-04T14:00:00Z"


def test_header_mismatch_is_rejected(spark, tmp_path):
    (tmp_path / "reviews.csv").write_text(
        "id,booking_id,score,comment,created_at\n"
        "x,y,5,ok,2026-08-04T16:00:00Z\n",
        encoding="utf-8",
    )
    with pytest.raises(Exception):
        read_table(spark, "reviews", tmp_path).collect()


def test_date_written_as_text_is_rejected(spark, tmp_path):
    (tmp_path / "reviews.csv").write_text(
        "id,booking_id,rating,comment,created_at\n"
        "x,y,5,ok,cuatro de agosto\n",
        encoding="utf-8",
    )
    with pytest.raises(Exception):
        read_table(spark, "reviews", tmp_path).collect()


def test_fixture_bookings_reference_existing_offerings(tables):
    orphans = tables["bookings"].join(
        tables["offerings"], tables["bookings"].offering_id == tables["offerings"].id, "left_anti"
    )
    assert orphans.count() == 0


def test_fixture_reviews_only_for_completed_bookings(tables):
    bookings = tables["bookings"].select(F.col("id").alias("booking_id"), "status")
    not_completed = tables["reviews"].join(bookings, "booking_id").where(F.col("status") != "COMPLETED")
    assert not_completed.count() == 0
