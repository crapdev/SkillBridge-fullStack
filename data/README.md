# data · Pipeline PySpark y QA de datos

Proyecto Python independiente del backend. Corre Spark en modo local (sin clúster) dentro de Docker,
con Python 3.12 y Java 21, para que todos tengan el mismo entorno.

Contexto completo para el equipo (decisiones, contrato, frentes de trabajo y recetas):
[docs/DATA-PIPELINE.md](../docs/DATA-PIPELINE.md). Cómo trabajar tu HU: [docs/GUIA-HU-DATOS.md](../docs/GUIA-HU-DATOS.md).

## Comandos

```bash
make data-build   # construye la imagen (la primera vez descarga PySpark, ~400 MB)
make data-test    # corre todas las pruebas
make data-shell   # consola dentro del contenedor (pytest -k ..., python -m ...)
```

La carpeta `data/` se monta en el contenedor: los cambios de código se ven sin reconstruir.
Solo hace falta `make data-build` si cambian las dependencias de `pyproject.toml`.

### Sin Docker (Windows)

Con Python 3.12 y JDK 21 instalados: `pip install ".[dev]"` y `pytest` dentro de `data/`.
Si Spark falla con `failed to create a child event loop` / `UnixDomainSockets.connect`, la ruta de `%TEMP%`
es demasiado larga o tiene espacios. Antes de `pytest`, en PowerShell:
`$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Public"`.

## Estructura

| Ruta | Qué contiene |
|---|---|
| `src/skillbridge_data/schemas.py` | **Contrato**: esquema de cada tabla y dominios permitidos. Se cambia junto con las migraciones. |
| `src/skillbridge_data/readers.py` | Lectura de CSV con esquema explícito; falla si el encabezado o un tipo no coincide. |
| `src/skillbridge_data/session.py` | SparkSession local en UTC. |
| `tests/fixtures/*.csv` | Datos pequeños, sintéticos y **correctos**. Base para probar métricas y reglas sin esperar al generador. |
| `src/skillbridge_data/metrics/` | Una métrica por archivo. `ejemplo_reservas_por_estado.py` es el modelo a copiar. |
| `src/skillbridge_data/quality/` | Una regla de calidad por archivo. `ejemplo_completitud.py` es el modelo a copiar. |
| `tests/conftest.py` | Fixtures `spark`, `fixtures_dir`, `tables` y `parquet_dir`. |
| `output/` | Resultados (crudo, limpio, métricas). No se versiona. |

## Reglas

- Solo datos sintéticos. El contrato excluye contraseñas, nombres y correos.
- Las fechas son instantes UTC (`2026-08-04T14:00:00Z`). Ojo: `collect()`/`first()` devuelven `datetime`
  sin zona en la hora local del equipo; para comparar horas, formatéalas dentro de Spark (`F.date_format`).
- La tabla `reviews` aún no existe en el backend: por ahora es solo sintética.
- Una prueba de calidad debe pasar con los fixtures y fallar con un defecto inyectado.
- Si se modifica un fixture, se actualiza `EXPECTED_ROWS` en `tests/test_contract.py`.
