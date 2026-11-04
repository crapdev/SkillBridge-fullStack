"""Contrato de datos: esquema de cada tabla tal como llega al pipeline.

Refleja las migraciones Flyway de backend/src/main/resources/db/migration y los enums
de domain/model. Si cambia una migración o un enum, se actualiza este archivo en el mismo PR.

Reglas del contrato:
  * Los identificadores (UUID) viajan como texto.
  * Las fechas son instantes en UTC (TIMESTAMPTZ en PostgreSQL).
  * Nunca se exportan datos personales: ni contraseñas, ni nombres, ni correos.
  * Spark trata todas las columnas de un CSV como anulables aunque aquí digan nullable=False:
    los nulos se verifican con las pruebas de calidad, no con el esquema.
"""
from pyspark.sql.types import (
    BooleanType,
    DecimalType,
    IntegerType,
    StringType,
    StructField,
    StructType,
    TimestampType,
)

# Dominios permitidos (enums del backend) -------------------------------------------------------
ROLES = ("CUSTOMER", "PROVIDER", "ADMIN")
ACCOUNT_STATUSES = ("ACTIVE", "PENDING_APPROVAL", "REJECTED", "INACTIVE")
OFFERING_LEVELS = ("BASICO", "INTERMEDIO", "AVANZADO")
OFFERING_REVIEW_STATUSES = ("PENDING_REVIEW", "APPROVED", "REJECTED")
BOOKING_STATUSES = ("CREATED", "CONFIRMED", "CANCELLED", "COMPLETED")
PAYMENT_STATUSES = ("PENDING", "PAID", "FAILED")

RATING_MIN, RATING_MAX = 1, 5

# Tablas ----------------------------------------------------------------------------------------
# app_users sin password, name ni email
USERS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("role", StringType(), nullable=False),
    StructField("status", StringType(), nullable=False),
    StructField("created_at", TimestampType(), nullable=False),
])

OFFERINGS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("provider_id", StringType(), nullable=True),   # anulable en V2
    StructField("title", StringType(), nullable=False),
    StructField("category", StringType(), nullable=False),
    StructField("price", DecimalType(12, 2), nullable=False),
    StructField("level", StringType(), nullable=False),
    StructField("duration_minutes", IntegerType(), nullable=False),  # 30-240 (V8)
    StructField("review_status", StringType(), nullable=False),
    StructField("active", BooleanType(), nullable=False),
    StructField("created_at", TimestampType(), nullable=False),
])

BOOKINGS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("offering_id", StringType(), nullable=False),
    StructField("customer_id", StringType(), nullable=False),
    StructField("scheduled_at", TimestampType(), nullable=False),  # inicio; el fin es + duration_minutes
    StructField("status", StringType(), nullable=False),
    StructField("created_at", TimestampType(), nullable=False),
])

AVAILABILITY_SLOTS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("offering_id", StringType(), nullable=False),
    StructField("scheduled_at", TimestampType(), nullable=False),
    StructField("reserved", BooleanType(), nullable=False),
])

PAYMENTS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("booking_id", StringType(), nullable=False),
    StructField("amount", DecimalType(12, 2), nullable=False),
    StructField("currency", StringType(), nullable=False),
    StructField("status", StringType(), nullable=False),
    StructField("created_at", TimestampType(), nullable=False),
])

# Aún no existe en el backend: por ahora solo hay reseñas sintéticas.
# Si se agrega la migración de reseñas, debe respetar estas columnas.
REVIEWS = StructType([
    StructField("id", StringType(), nullable=False),
    StructField("booking_id", StringType(), nullable=False),
    StructField("rating", IntegerType(), nullable=False),          # 1-5
    StructField("comment", StringType(), nullable=True),
    StructField("created_at", TimestampType(), nullable=False),
])

# Nombre de la tabla (y del archivo <nombre>.csv) -> esquema
TABLES: dict[str, StructType] = {
    "users": USERS,
    "offerings": OFFERINGS,
    "bookings": BOOKINGS,
    "availability_slots": AVAILABILITY_SLOTS,
    "payments": PAYMENTS,
    "reviews": REVIEWS,
}
