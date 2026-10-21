# Entorno de PySpark idéntico para todos: Python 3.12 + Java 21 (el mismo del backend).
# Evita instalar Java/winutils en Windows, que es donde PySpark suele fallar al escribir Parquet.
FROM eclipse-temurin:21-jre AS java

FROM python:3.12-slim-bookworm

COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src

WORKDIR /app

# Dependencias primero para aprovechar la caché de capas
COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir ".[dev]"

COPY tests ./tests

CMD ["pytest"]
