# Guía para trabajar tu HU de datos con Spark

> Para quien **nunca ha usado Spark**. Léela completa una vez (unos 20 minutos) y después úsala como consulta.
> Tu HU está en Jira. El contexto del proyecto, en [DATA-PIPELINE.md](DATA-PIPELINE.md).

## Qué vas a hacer

Tu HU tiene dos partes, y las dos se hacen con Spark:

1. **Una métrica:** una función que recibe tablas y calcula algo (por ejemplo, reservas por mes).
2. **Una regla de calidad:** una función que busca datos dañados, con una prueba que demuestra que los encuentra.

No necesitas base de datos, ni backend, ni esperar a nadie. Trabajas con unos datos de prueba pequeños que ya están
en `data/tests/fixtures/`, y la HU te dice qué resultado exacto debe dar tu función.

---

## 1. Preparar tu entorno (una sola vez)

### Opción A · Con Docker (recomendada)

Con Docker Desktop encendido, desde la raíz del proyecto:

```bash
make data-build
```

```bash
make data-test
```

Si no tienes `make`, el equivalente es `docker compose --profile data run --rm data pytest`.

Debe terminar con **21 passed**. Si ves eso, estás listo.

### Opción B · Sin Docker (Windows)

Necesitas **Python 3.12** (PySpark aún no funciona con 3.14) y **JDK 21**. En PowerShell, dentro de `data/`:

```bash
python -m venv .venv
```

```bash
.venv\Scripts\activate
```

```bash
pip install ".[dev]"
```

Antes de correr pruebas, en cada consola nueva:

```bash
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Public"
```

```bash
pytest
```

Sin Docker, las pruebas que escriben Parquet se **omiten** (salen como `skipped`). Es normal: Spark no puede
escribir Parquet en Windows sin herramientas extra. Esas pruebas sí corren en Docker y en el CI.

---

## 2. Spark en 5 ideas

1. **Un DataFrame es una tabla.** Tiene columnas con nombre y tipo, como una tabla de PostgreSQL. En las pruebas
   ya las tienes cargadas: `tables["bookings"]`, `tables["offerings"]`, etc.
2. **Las operaciones devuelven tablas nuevas.** `df.where(...)` no cambia `df`: devuelve otra tabla. Por eso se
   encadenan una tras otra.
3. **Spark es perezoso.** `where`, `join` o `groupBy` solo **preparan** el cálculo. Spark trabaja de verdad cuando
   pides un resultado: `show()`, `count()`, `collect()` o escribir un archivo.
4. **Las columnas se escriben con `F.col("nombre")`.** `F` es `pyspark.sql.functions`, la caja de herramientas de
   Spark: contar, sumar, redondear, formatear fechas…
5. **Nada de ciclos `for` sobre las filas.** En Spark se describe **qué** quieres (agrupa, cuenta, une) y Spark
   decide cómo hacerlo. Si estás escribiendo un `for` sobre filas, hay una función de `F` que lo hace mejor.

---

## 3. Chuleta: lo que vas a necesitar

Todas asumen `from pyspark.sql import functions as F` y `from pyspark.sql import Window`.

| Quiero… | Código | Lo usa |
|---|---|---|
| Ver la tabla | `df.show()` | Todos |
| Contar filas | `df.count()` | Todos |
| Quedarme con unas columnas | `df.select("id", "status")` | Todos |
| Filtrar filas | `df.where(F.col("status") == "CANCELLED")` | HU-46, HU-48, HU-50 |
| Crear o cambiar una columna | `df.withColumn("mes", F.date_format("scheduled_at", "yyyy-MM"))` | HU-44, HU-48, HU-49 |
| Agrupar y contar | `df.groupBy("provider_id").agg(F.count("*").alias("reservas"))` | Todos |
| Sumar / promediar | `F.sum("amount")`, `F.avg("rating")` | HU-47, HU-48 |
| Contar con condición | `F.sum((F.col("status") == "CANCELLED").cast("int"))` | HU-45, HU-46 |
| Redondear | `F.round(F.col("x"), 2)` | HU-45, HU-46, HU-47, HU-50 |
| Unir dos tablas | `a.join(b, "offering_id")` | HU-44, HU-46, HU-47, HU-48, HU-49 |
| Filas de A **sin** pareja en B | `a.join(b, "offering_id", "left_anti")` | HU-45, HU-46 |
| Ordenar | `df.orderBy("mes", F.desc("reservas"))` | Todos |
| Puesto dentro de un grupo | `F.rank().over(Window.partitionBy("mes").orderBy(F.desc("reservas")))` | HU-49 |
| Valor de la fila anterior | `F.lag("scheduled_at").over(Window.partitionBy("customer_id").orderBy("scheduled_at"))` | HU-49, HU-50 |
| Días entre dos fechas | `F.datediff(F.to_date("fin"), F.to_date("inicio"))` | HU-50 |
| Sumar minutos a una fecha | `F.col("scheduled_at") + F.make_interval(mins=F.col("duration_minutes"))` | HU-49 |
| Pegar filas de otra tabla | `df.union(otra)` | Defectos |
| Traer filas a Python | `df.collect()` → lista de filas; `fila["columna"]` | Pruebas |
| Guardar en Parquet | `df.write.mode("overwrite").parquet(ruta)` | HU-50 |

### El error más común en los joins: columnas con el mismo nombre

`bookings` y `offerings` tienen las dos una columna `id`. Si las unes tal cual, Spark no sabe a cuál te refieres.
Renombra antes de unir y une por el nombre en común:

```python
offerings = tables["offerings"].select(
    F.col("id").alias("offering_id"),   # ahora se llama igual que en bookings
    "provider_id",
)
reservas_con_proveedor = tables["bookings"].join(offerings, "offering_id")
```

### Qué es una ventana

Un `groupBy` **resume**: de muchas filas deja una por grupo. Una **ventana** mira el grupo **sin resumirlo**: cada
fila sigue ahí, pero puede saber su puesto dentro del grupo (`rank`) o qué valor tenía la fila anterior (`lag`).

```python
por_cliente = Window.partitionBy("customer_id").orderBy("scheduled_at")   # grupos por cliente, en orden de fecha
df.withColumn("fecha_anterior", F.lag("scheduled_at").over(por_cliente))  # la primera reserva de cada cliente queda en null
```

---

## 4. Recorre el ejemplo antes de empezar

Abre estos cuatro archivos en orden y lee los comentarios. Tu HU sigue exactamente el mismo patrón:

1. `data/src/skillbridge_data/metrics/ejemplo_reservas_por_estado.py`: la métrica, un solo `groupBy`.
2. `data/tests/test_metrics/test_ejemplo_reservas_por_estado.py`: cómo se prueba con el resultado esperado.
3. `data/src/skillbridge_data/quality/ejemplo_completitud.py`: una regla que devuelve las filas dañadas.
4. `data/tests/test_quality/test_ejemplo_completitud.py`: las dos pruebas (datos correctos y defecto inyectado).

Córrelas solas para ver que pasan:

```bash
docker compose --profile data run --rm data pytest tests/test_metrics tests/test_quality -v
```

---

## 5. Paso a paso de tu HU

### Paso 1 · Crea tu rama

```bash
git checkout -b feature/data-<tu-hu>
```

Por ejemplo, `feature/data-reservas-por-mes`. El CI solo corre en ramas que empiezan por `feature/` o `fix/`.

### Paso 2 · Mira los datos con tus propios ojos

Abre una consola de Python con Spark (`make data-shell` y luego `python`; sin Docker, en `data/` con
`$env:PYTHONPATH="src"` y `python`):

```python
from skillbridge_data.session import build_spark
from skillbridge_data.readers import read_all

spark = build_spark()
tables = read_all(spark, "tests/fixtures")
tables["bookings"].show(truncate=False)
```

Prueba aquí, línea por línea, las operaciones de tu HU antes de escribirlas en un archivo. Es la forma más rápida
de aprender.

### Paso 3 · Escribe primero la prueba de la métrica

Copia `test_ejemplo_reservas_por_estado.py` como `tests/test_metrics/test_<tu_metrica>.py` y pon el resultado
esperado que trae tu HU. La prueba va a fallar porque la función aún no existe. Es lo esperado.

### Paso 4 · Escribe la métrica

Copia el ejemplo como `src/skillbridge_data/metrics/<tu_metrica>.py` y cambia el cuerpo. Reglas:

- Recibe DataFrames y devuelve un DataFrame, con las columnas exactas que pide la HU.
- No crea la SparkSession ni lee archivos: eso lo hacen las pruebas.
- Termina con un `orderBy` para que el resultado siempre salga en el mismo orden.

Corre solo tu prueba hasta que pase:

```bash
docker compose --profile data run --rm data pytest tests/test_metrics/test_<tu_metrica>.py -v
```

### Paso 5 · Escribe la regla de calidad

Copia `ejemplo_completitud.py` como `src/skillbridge_data/quality/<tu_regla>.py`. La función devuelve las filas que
**incumplen** la regla.

### Paso 6 · Prueba la regla dos veces

En `tests/test_quality/test_<tu_regla>.py`:

1. **Con los fixtures** debe devolver 0 filas (no hay falsos positivos).
2. **Con el defecto inyectado** debe encontrarlo. El defecto se crea sobre una copia en memoria, nunca editando los
   CSV. Dos formas de hacerlo:

```python
# Cambiar un valor de una fila existente
con_defecto = tables["reviews"].withColumn(
    "rating", F.when(F.col("id").endswith("0001"), F.lit(9)).otherwise(F.col("rating"))
)

# Agregar una fila nueva: se copia una fila que ya existe y se le cambian valores
fila_mala = (
    tables["bookings"]
    .where(F.col("id").endswith("0001"))                        # copia de la reserva ...0001
    .withColumn("id", F.lit("c-defecto"))                        # id nuevo
    .withColumn("offering_id", F.lit("b-no-existe"))             # el defecto: mentoría inexistente
)
con_defecto = tables["bookings"].union(fila_mala)

# Para una fecha: F.to_timestamp(F.lit("2026-08-04T14:30:00Z"))
```

Además de contar, comprueba que la regla señala **la fila correcta** (`malas.first()["id"]`).

### Paso 7 · Corre todo y abre el PR

```bash
make data-test
```

Commit con la clave de Jira, como en el resto del proyecto:

```bash
git commit -m "feat(SCRUM-XXX): metrica de reservas por mes por proveedor"
```

Abre el PR hacia `develop` y pide revisión a tu compañero de apoyo y a alguien de otro nivel.

---

## 6. Errores frecuentes

| Ves esto | Qué pasa | Qué hacer |
|---|---|---|
| `failed to create a child event loop` / `UnixDomainSockets.connect` | Windows sin Docker: la carpeta temporal tiene espacios | `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Public"` |
| `HADOOP_HOME and hadoop.home.dir are unset` | Escribir Parquet en Windows sin Docker | Correr esa prueba con Docker (`make data-test`) |
| `AMBIGUOUS_REFERENCE` o `ambiguous` en un join | Dos tablas con una columna del mismo nombre (`id`) | Renombrar antes de unir (sección 3) |
| `UNRESOLVED_COLUMN` | Nombre de columna mal escrito | `df.printSchema()` para ver los nombres reales |
| La hora sale 5 horas antes (14:00 → 09:00) | `collect()` convierte a la hora de Colombia | Compara meses o fechas con `F.date_format` dentro de Spark |
| `0.1 + 0.2 != 0.3`, o promedios con muchos decimales | Números decimales | Redondea con `F.round(..., 2)` como pide la HU |
| El resultado sale en distinto orden cada vez | Spark no garantiza orden | `orderBy` al final de la métrica |
| `Python worker exited unexpectedly (crashed)` | Windows sin Docker: `spark.createDataFrame(...)` con datos de Python o una UDF | Crear filas copiando una existente con `withColumn` (paso 6), o correr con Docker |
| `ModuleNotFoundError: skillbridge_data` en la consola | Python no encuentra `src/` | Ejecutar desde `data/` con `$env:PYTHONPATH="src"` o usar `make data-shell` |
| `AssertionError` con `Decimal('360000.00')` | Los montos son `Decimal`, no `float` | Comparar con `Decimal("360000.00")` (`from decimal import Decimal`) |

---

## 7. Antes de pedir revisión

- [ ] Mi métrica da exactamente los valores de la HU con los fixtures.
- [ ] Mi regla devuelve 0 filas con los fixtures y encuentra el defecto inyectado.
- [ ] No modifiqué los CSV de `tests/fixtures/` ni `schemas.py`.
- [ ] `make data-test` pasa completo.
- [ ] Mis archivos tienen nombres propios (no pisan los de otra HU).
- [ ] Puedo explicar mi código sin leerlo: qué hace cada `join`, `groupBy` o ventana.

## 8. Si te trabas

1. Prueba la línea que falla en la consola (paso 2) con `show()` después de cada operación.
2. Más de 20 minutos sin avanzar: llama a tu compañero de apoyo.
3. Sigue sin salir: llévalo a la daily como bloqueo. Probablemente a otra persona le pasó lo mismo.
