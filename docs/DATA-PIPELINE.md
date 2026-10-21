# Pipeline de datos con PySpark · Contexto para el equipo

> **Ruta de aprendizaje:** ejes 2 (Python y Spark) y 3 (QA de datos) · **Carpeta:** `data/`
> **Estado:** esqueleto listo, 17 pruebas en verde. Falta el generador, el job de métricas y la suite de calidad.

Este documento explica qué se agregó al proyecto para trabajar con Spark, por qué se hizo así y cómo puede
avanzar todo el equipo en paralelo sin pisarse. Si vas a tocar algo de `data/`, léelo primero.

---

## 1. Qué se agregó

| Archivo | Para qué sirve |
|---|---|
| `data/pyproject.toml` | Dependencias (PySpark 4.0.1, pytest, Faker) y configuración de pytest. |
| `data/src/skillbridge_data/schemas.py` | **Contrato de datos**: columnas, tipos y valores permitidos de cada tabla. |
| `data/src/skillbridge_data/readers.py` | Lee un CSV aplicando su esquema; falla si algo no coincide. |
| `data/src/skillbridge_data/session.py` | SparkSession local, en UTC y con memoria limitada. |
| `data/tests/fixtures/*.csv` | Datos pequeños, sintéticos y **correctos** de cada tabla. |
| `data/tests/conftest.py` | Fixtures `spark`, `fixtures_dir` y `tables` para todas las pruebas. |
| `data/tests/test_contract.py` | 17 pruebas que verifican que contrato y fixtures están de acuerdo. |
| `data/Dockerfile` | Python 3.12 + Java 21: el mismo entorno para todos. |
| `docker-compose.yml` | Servicio `data` con perfil `data` (no arranca con `make up`). |
| `Makefile` | `make data-build`, `make data-test`, `make data-shell`. |
| `.github/workflows/ci.yml` | Job `data` (pytest + reporte) y build de la imagen en el job `docker`. |
| `.gitignore` | Ignora `data/output/` y cachés de Python. |

**El backend y el frontend no se modificaron.** El pipeline es un proyecto aparte que solo lee datos exportados.

---

## 2. Decisiones y por qué

1. **Proyecto independiente en `data/`.** Quien trabaja en Spark no necesita compilar el backend, y quien trabaja
   en el backend no se ve afectado. Detalle en [ADR-0003](adr/0003-pyspark-data-pipeline.md).
2. **Contrato primero.** `schemas.py` se acordó antes de escribir jobs o pruebas. Todos programan contra las mismas
   columnas y tipos; si alguien lo cambia, `test_contract.py` falla de inmediato.
3. **Nombres reales del proyecto.** La ruta habla de "mentor" y `mentor_profile`; aquí el mentor es el
   **proveedor** (`offerings.provider_id`) y la mentoría es la **offering**. Las métricas y reglas del Anexo A se
   traducen a estas tablas.
4. **Sin datos personales.** El contrato de `users` solo tiene `id`, `role`, `status` y `created_at`. Nunca se
   exportan contraseñas, nombres ni correos.
5. **Fechas en UTC.** Igual que en PostgreSQL (`TIMESTAMPTZ`). La sesión de Spark fija `spark.sql.session.timeZone=UTC`.
6. **Spark en Docker.** En Windows, PySpark suele fallar al escribir Parquet (pide `winutils.exe`) o por la versión
   de Java. El contenedor elimina ese problema para todos.
7. **Fixtures pequeños escritos a mano.** Permiten que los equipos de métricas y de calidad empiecen el día 1,
   sin esperar al generador con Faker.

---

## 3. El contrato de datos

| Tabla | Columnas | Origen |
|---|---|---|
| `users` | id, role, status, created_at | `app_users` (sin password, name ni email) |
| `offerings` | id, provider_id, title, category, price, level, duration_minutes, review_status, active, created_at | `offerings` |
| `bookings` | id, offering_id, customer_id, scheduled_at, status, created_at | `bookings` |
| `availability_slots` | id, offering_id, scheduled_at, reserved | `availability_slots` |
| `payments` | id, booking_id, amount, currency, status, created_at | `payments` |
| `reviews` | id, booking_id, rating, comment, created_at | **No existe en el backend**: solo sintética |

Valores permitidos (copiados de los enums de `domain/model`):

- `BookingStatus`: `CREATED`, `CONFIRMED`, `CANCELLED`, `COMPLETED`
- `Role`: `CUSTOMER`, `PROVIDER`, `ADMIN`
- `AccountStatus`: `ACTIVE`, `PENDING_APPROVAL`, `REJECTED`, `INACTIVE`
- `OfferingLevel`: `BASICO`, `INTERMEDIO`, `AVANZADO`
- `OfferingReviewStatus`: `PENDING_REVIEW`, `APPROVED`, `REJECTED`
- `PaymentStatus`: `PENDING`, `PAID`, `FAILED`
- Calificación de reseña: 1 a 5

> **Regla:** si una migración Flyway o un enum cambia, `schemas.py` y los fixtures se actualizan **en el mismo PR**.

**Spark considera anulables todas las columnas de un CSV**, aunque el esquema diga `nullable=False`. Los nulos se
detectan con pruebas de calidad, no con el esquema.

---

## 4. Los fixtures

Están en `data/tests/fixtures/` y son coherentes entre sí:

| Tabla | Filas | Qué contienen |
|---|---|---|
| users | 7 | 1 admin, 2 proveedores, 4 clientes (uno inactivo) |
| offerings | 4 | 3 aprobadas y 1 pendiente de revisión |
| bookings | 12 | Agosto–octubre 2026, todos los estados, sin solapes por proveedor |
| availability_slots | 14 | Una por reserva (libre si se canceló) + 2 libres |
| payments | 10 | PAID, FAILED (reserva cancelada) y PENDING |
| reviews | 6 | Solo de reservas COMPLETED, calificaciones de 2 a 5, una sin comentario |

Los IDs son fáciles de leer: prefijo `a…` usuarios, `b…` offerings, `c…` bookings, `d…` slots, `e…` payments y
`f…` reviews (`c0000000-0000-0000-0000-000000000001` es la reserva 1).

**Son datos correctos a propósito.** Una prueba de calidad debe **pasar** con ellos y **fallar** cuando se les
inyecta un defecto. No se editan para hacer pasar una prueba: si cambian, se actualiza `EXPECTED_ROWS` en
`test_contract.py`.

---

## 5. Cómo ejecutarlo

### Con Docker (recomendado)

```bash
make data-build   # la primera vez descarga PySpark (~400 MB)
make data-test    # corre todas las pruebas
make data-shell   # consola dentro del contenedor
```

Sin `make` (por ejemplo en PowerShell):

```bash
docker compose --profile data run --rm data pytest
```

La carpeta `data/` se monta en el contenedor: los cambios de código se ven sin reconstruir. Solo hay que
reconstruir si cambia `pyproject.toml`.

### Sin Docker (Windows)

Requiere Python 3.10–3.13 (PySpark 4.0 aún no soporta 3.14) y JDK 17 o 21.

```bash
cd data
python -m venv .venv
.venv\Scripts\activate
pip install ".[dev]"
pytest
```

---

## 6. Problemas conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| `failed to create a child event loop` o `UnixDomainSockets.connect` al crear la sesión | En Windows, la ruta de `%TEMP%` tiene espacios o es muy larga (ej. `Cohorte 5`) y rompe las conexiones internas de Java | En PowerShell, antes de `pytest`: `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Public"` |
| Una hora sale 5 horas antes (14:00 → 09:00) | `collect()` / `first()` convierten la fecha a la hora local del equipo (Colombia, UTC-5) | Comparar horas dentro de Spark con `F.date_format(col, "yyyy-MM-dd'T'HH:mm:ssXXX")`. Es el mismo tipo de error que [BUG-04](BUG-04-fecha-del-pago-en-utc.md) |
| `pip install` falla con Python 3.14 | PySpark 4.0.1 soporta hasta 3.13 | Usar Docker o Python 3.12 |
| `failed to connect to the docker API` | Docker Desktop apagado | Encender Docker Desktop |

---

## 7. Convenciones

- **Ramas:** `feature/data-<tema>`. El CI corre al hacer push solo en `feature/**` y `fix/**` (y en PR hacia
  `main` o `develop`); una rama con otro nombre no se valida hasta abrir el PR.
- **Nada de datos reales** en el repositorio, ni siquiera en `output/` (está en `.gitignore`).
- **Una prueba sin defecto inyectado no prueba nada:** se debe demostrar que falla cuando debe.
- **Medir antes y después** cualquier optimización (caché de DataFrames, particiones) con `explain()` o tiempos.

---
