# ADR-0003: Add the PySpark data pipeline as an independent project

## Status
Accepted for the academic environment.

## Decision
Add a separate Python project in `data/` that runs PySpark in local mode inside Docker (Python 3.12 + Java 21). It reads CSV files exported from PostgreSQL through an explicit data contract (`schemas.py`) and is tested with pytest against small, hand-written fixtures. The backend and frontend stay unchanged.

## Rationale
- The learning path adds Spark and data QA without touching the transactional system: the pipeline only consumes exported data.
- A shared contract and committed fixtures let the generator, metrics and data-quality work start in parallel on day one.
- Docker avoids the usual PySpark issues on Windows (winutils, Java version mismatch, long temp paths).
- The contract excludes personal data (password, name, email) and keeps every timestamp in UTC.

## Consequences
- Any Flyway migration or enum change must update `data/src/skillbridge_data/schemas.py` and the fixtures in the same PR.
- CI runs a `data` job (Java 21 + Python 3.12 + pytest), which adds a few minutes per run.
- `reviews` exists only as synthetic data until the backend adds that table.
- Spark runs in local mode only; no cluster or paid service is required.
