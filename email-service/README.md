# SkillBridge Email Service

Microservicio NestJS que envía los correos de SkillBridge fuera del backend Java. Un fallo al enviar un correo no
afecta a las reservas, los pagos ni a las notificaciones de la campana: Java publica el evento y sigue.

## Estado

| Parte | Estado |
|---|---|
| Health `GET /api/health` | Implementado |
| Consumidor de eventos de reservas (Persona B) | Implementado y probado contra RabbitMQ y PostgreSQL reales |
| Endpoint de contexto en Java `GET /internal/bookings/{id}/email-context` | **Pendiente (backend Java)**. Sin él el consumidor no arranca |
| `EmailSenderPort`, adaptadores Mailpit/Brevo, layout común (Persona A) | **Pendiente**. Solo existe `FakeEmailSenderAdapter` |
| Verificación de correo (HU #39) | En la rama `feature/email-verification` |

## Requisitos

- Node.js 22 y npm 10.
- Docker Compose (RabbitMQ y PostgreSQL del `docker-compose.yml` principal; Mailpit del de este directorio).

## Ejecución local

```bash
# desde la raíz del repositorio: broker y base principales (no se levanta un segundo RabbitMQ)
docker compose up -d rabbitmq postgres backend   # backend aplica Flyway, incluida la V22
# desde email-service/
cp .env.example .env        # completar BOOKING_CONTEXT_URL / INTERNAL_API_TOKEN cuando Java exponga el endpoint
docker compose up -d        # solo Mailpit (http://localhost:8025)
npm ci
npm run start:dev
```

- Health: http://localhost:3000/api/health
- RabbitMQ Management: http://localhost:15672 (guest / guest)
- El compose principal publica AMQP en `127.0.0.1:5672` y PostgreSQL en `127.0.0.1:5433`.
- Con el perfil `observability`, Grafana también usa el puerto 3000: en ese caso arrancar con `PORT=3002`.

Dentro de la red Docker `skillbridge`: `RABBITMQ_URL=amqp://guest:guest@rabbitmq:5672`,
`DATABASE_URL=postgres://skillbridge:skillbridge_dev@postgres:5432/skillbridge`.

---

# Correos de reservas (Persona B)

## Arquitectura

```
Java (BookingService, ProcessPaymentService, BookingCompletionService)
  └─ publica en booking.events (topic, durable)  routing key = tipo de evento
        ├─ booking.*  ─────────────────▶ booking.notifications.queue  (Java: campana; no se toca)
        └─ created|paid|cancelled|completed ─▶ booking.emails.queue ─▶ BookingEventsConsumer
                                                        │ rechazo/agotado      ▲ caduca
                                                        ▼                      │
                          booking.emails.dlx (fanout) ─▶ booking.emails.dlq   booking.emails.retry.queue
```

Flujo de cada mensaje (`src/modules/booking-events/`):

1. `BookingEventsConsumer` lo recibe con ACK manual (`prefetch` = `RABBITMQ_PREFETCH`).
2. `parseBookingEvent` valida el JSON; la routing key (o la cabecera `x-original-routing-key` si viene de un reintento) da el tipo.
3. `BookingEmailProcessor` pide el contexto (`BookingContextPort`), resuelve destinatarios, y por cada uno reclama el envío en
   el registro (`DeliveryLedgerPort`), renderiza la plantilla, envía con el `EmailSenderPort` de Persona A y marca el resultado.
4. El consumidor decide: `ack`, reintento o DLQ.

`RabbitMqService` mantiene una única conexión (`amqp-connection-manager`): reconecta solo y, al reconectar, vuelve a declarar la
topología y a registrar el consumidor. Sin `RABBITMQ_URL` el servicio arranca sin consumir.

## Topología

| Objeto | Tipo / argumentos | Para qué |
|---|---|---|
| `booking.events` | topic, durable (igual que `RabbitConfiguration` de Java) | Exchange de Java |
| `booking.emails.queue` | durable, `x-dead-letter-exchange=booking.emails.dlx` | Nuestra cola; enlazada a los 4 eventos |
| `booking.emails.dlx` | fanout, durable | Recibe lo rechazado conservando la routing key original |
| `booking.emails.dlq` | durable | Mensajes que no se pudieron procesar |
| `booking.emails.retry.queue` | durable, `x-dead-letter-exchange=""`, `x-dead-letter-routing-key=booking.emails.queue` | Espera entre intentos |

- `booking.payment-failed` no se enlaza: queda fuera del alcance.
- La cola de reintentos devuelve los mensajes por el exchange por defecto **solo a `booking.emails.queue`**. Si volvieran por
  `booking.events`, la cola de notificaciones de Java recibiría el evento repetido.
- La espera va en cada mensaje (`expiration`), no como `x-message-ttl` de la cola: cambiar `BOOKING_EMAIL_RETRY_DELAY_MS` no
  obliga a borrar la cola (RabbitMQ responde `PRECONDITION_FAILED` si cambian los argumentos de una cola existente).

## Contrato del evento (verificado en el código Java)

`infrastructure/adapter/out/messaging/BookingEvent.java`, publicado por `RabbitBookingEventPublisher` con
`new Jackson2JsonMessageConverter()`:

```json
{"bookingId":"uuid","offeringId":"uuid","customerId":"uuid","scheduledAt":1795186800.000000000,"occurredAt":1794615143.739838807}
```

- Los cuatro eventos comparten el payload; el tipo va **solo** en la routing key.
- Los `Instant` llegan como **segundos epoch con decimales** (verificado con spring-amqp 3.2.7 / jackson-databind 2.19.2). Se
  acepta también ISO-8601 por si el conversor cambia.
- No hay `eventId` ni `messageId`, ni nombres ni emails.
- Java publica **dentro de la transacción**: el evento puede llegar antes del commit. Por eso "reserva no encontrada" es reintentable.

## Contexto de la reserva (pendiente en Java)

El evento no trae los datos de los correos. Contrato **propuesto** al backend Java (módulo de reservas). No existe todavía
y el email-service no lo simula:

```
GET {BOOKING_CONTEXT_URL}/internal/bookings/{bookingId}/email-context
X-Internal-Token: {INTERNAL_API_TOKEN}

200 {
  "bookingId": "uuid",
  "scheduledAt": "2026-11-20T15:00:00Z",
  "durationMinutes": 60,
  "offering": { "title": "Mentoría Java Backend" },
  "customer": { "name": "Ana", "email": "ana@example.com" },
  "provider": { "name": "Luis", "email": "luis@example.com" }
}
```

Datos en Java: `bookings` → `offerings` (`title`, `duration_minutes`, `provider_id`) → `app_users` (`name`, `email`) del cliente
y del proveedor. La ruta `/internal/**` no debe ser pública: el token se compara en Java y no se registra en ningún log.

| Respuesta | Tratamiento |
|---|---|
| 200 válido | se envían los correos |
| 404 | reintentable (puede ser el commit aún no visible); si persiste, DLQ |
| 5xx, 429, timeout, red | reintentable |
| 401 / 403 | permanente (`context_unauthorized`): revisar `INTERNAL_API_TOKEN` |
| otros 4xx o cuerpo fuera de contrato | permanente |

Si `BOOKING_CONTEXT_URL` está vacía el consumidor **no arranca**: los eventos esperan en la cola durable en lugar de acabar en la DLQ.

## Destinatarios y plantillas

| Evento | Cliente (`/bookings/me`) | Proveedor (`/provider`) |
|---|---|---|
| `booking.created` | Reserva creada, falta pagar | Nueva reserva pendiente de pago |
| `booking.paid` | Pago confirmado | Reserva confirmada |
| `booking.cancelled` | Reserva cancelada | Reserva cancelada, horario libre |
| `booking.completed` | Invitación a calificar (HU-27) | Sesión completada |

- Fechas en `America/Bogota`, formato de 24 h: "viernes 20 de noviembre de 2026 a las 10:00 (hora de Colombia)".
- Si cliente y proveedor comparten dirección se envía un solo correo (el del cliente).
- La campana de Java solo avisa al proveedor; el correo es un canal independiente.
- `templates/provisional-layout.ts` contiene un layout mínimo y `escapeHtml` **provisionales**: cuando Persona A publique el
  layout común y sus helpers, se sustituye ese archivo y se borra.

## Idempotencia

Tabla `email.booking_email_deliveries` (Flyway `V22`, en el backend Java). Clave única `(booking_id, event_type, recipient_role)`:
la máquina de estados de la reserva (`CREATED → CONFIRMED → COMPLETED`, o `→ CANCELLED`) hace que cada evento ocurra una sola
vez por reserva, así que no hace falta `eventId`. No se usa `occurredAt` (cambia en cada publicación). Se guarda un registro
por destinatario: si el del cliente sale y el del proveedor falla, el reintento solo envía el del proveedor.

Estados: `PENDING` (registrado) → `PROCESSING` (reservado hasta `locked_until`) → `SENT` | `FAILED`.

- El reclamo es un único `INSERT … ON CONFLICT … DO UPDATE … WHERE` con `RETURNING`: con entregas simultáneas solo una gana.
- Un `PROCESSING` cuya reserva venció (proceso caído a mitad) puede retomarse.
- No se guardan emails, nombres ni el cuerpo del correo; `last_error` es un código técnico.
- `provider_message_id` existe pero queda vacío: `EmailSenderPort.send` devuelve `void`.

**Garantía real: al menos una vez.** Si el proveedor acepta el correo y el proceso cae (o falla la base) antes de guardar `SENT`,
el mensaje se reentrega y, al vencer la reserva, ese correo se reenvía. No existe "exactamente una vez" sin que el proveedor de
correo acepte una clave de idempotencia. Tras un reinicio la recuperación la hace RabbitMQ: lo no confirmado se reentrega.

## ACK, reintentos y DLQ

| Situación | Acción |
|---|---|
| Procesado (o ya enviado antes) | `ack` |
| JSON inválido, payload inválido, routing key desconocida | DLQ directa |
| Error permanente (contexto 401/403, contrato, `EmailDeliveryError.retryable=false`) | DLQ directa |
| Error reintentable (red, timeout, 5xx, 404 de contexto, proveedor caído, envío en curso) | copia en la cola de reintentos con `x-email-attempt+1` y espera `BOOKING_EMAIL_RETRY_DELAY_MS`, luego `ack` |
| Intentos agotados (`BOOKING_EMAIL_MAX_ATTEMPTS`) | DLQ con motivo `max_attempts:<motivo>` |
| No se puede publicar la copia | `nack` sin requeue → DLQ por la DLX de la cola |

- Nunca `requeue=true`: no hay bucles inmediatos.
- Los mensajes en la DLQ llevan `x-email-last-error`, `x-email-attempt` y `x-original-routing-key`.
- Errores desconocidos se tratan como reintentables (con intentos limitados). Un error con propiedad booleana `retryable`
  (el futuro `EmailDeliveryError` de Persona A) se respeta sin duplicar su clase.
- Si el canal se reconecta durante el procesamiento no se confirma en el canal nuevo: RabbitMQ reentrega el mensaje y la
  idempotencia evita el duplicado.
- Apagado (`SIGTERM`): deja de consumir, espera hasta 10 s a lo que está en curso y cierra canal y conexión.

## Variables de entorno

| Variable | Defecto | Descripción |
|---|---|---|
| `RABBITMQ_URL` | — | Vacía: no se consumen eventos |
| `RABBITMQ_EXCHANGE` / `_EMAIL_QUEUE` / `_EMAIL_DLX` / `_EMAIL_DLQ` / `_EMAIL_RETRY_QUEUE` | ver `.env.example` | Topología |
| `RABBITMQ_PREFETCH` | 5 | Mensajes simultáneos |
| `BOOKING_EMAIL_MAX_ATTEMPTS` | 5 | Intentos antes de la DLQ |
| `BOOKING_EMAIL_RETRY_DELAY_MS` | 30000 | Espera entre intentos |
| `BOOKING_EMAIL_LEASE_SECONDS` | 120 | Reserva de un envío en curso |
| `BOOKING_CONTEXT_URL` | — | Base del backend Java; vacía: el consumidor no arranca |
| `INTERNAL_API_TOKEN` | — | Cabecera `X-Internal-Token` |
| `BOOKING_CONTEXT_TIMEOUT_MS` | 5000 | Timeout de la petición de contexto |
| `DATABASE_URL` / `DATABASE_SSL` | ver `.env.example` | PostgreSQL compartido |
| `FRONTEND_URL` | http://localhost:4200 | Base de los enlaces de los correos |

## Pruebas

```bash
npm test                    # unitarias (sin Docker)
npm run build

# integración: RabbitMQ del compose principal + un PostgreSQL desechable (se borra su esquema email)
docker run -d --rm --name sb-email-test-pg -e POSTGRES_PASSWORD=test -e POSTGRES_DB=emailtest -p 127.0.0.1:55432:5432 postgres:17-alpine
DATABASE_TEST_URL=postgres://postgres:test@127.0.0.1:55432/emailtest \
RABBITMQ_TEST_URL=amqp://guest:guest@127.0.0.1:5672 \
RABBITMQ_TEST_MANAGEMENT_URL=http://guest:guest@127.0.0.1:15672 \
npm run test:integration
docker rm -f sb-email-test-pg
```

Los tests de integración usan exchange y colas con sufijo aleatorio: no tocan `booking.events` ni las colas de Java.

## Verificar RabbitMQ

```bash
curl -s -u guest:guest http://127.0.0.1:15672/api/queues/%2F/booking.emails.queue | jq '{messages, consumers, arguments}'
curl -s -u guest:guest http://127.0.0.1:15672/api/queues/%2F/booking.emails.dlq   | jq '{messages}'
# ver (y retirar) mensajes de la DLQ con su motivo
curl -s -u guest:guest -H 'content-type: application/json' -X POST \
  http://127.0.0.1:15672/api/queues/%2F/booking.emails.dlq/get -d '{"count":10,"ackmode":"ack_requeue_false","encoding":"auto"}' \
  | jq '.[] | {payload, headers: .properties.headers}'
```

## Errores conocidos y dependencias

- **Java**: falta el endpoint de contexto; hasta entonces el consumidor no arranca.
- **Persona A**: faltan `EmailSenderPort` definitivo, adaptadores Mailpit/Brevo y layout. `MAIL_PROVIDER` aún no cambia nada:
  siempre se usa el Fake. El contrato actual exige `recipientName`.
- **Orden de migraciones**: `V22` debe aplicarse después de la `V21` de HU #39. Si `V22` llega primero a una base existente,
  Flyway rechazará luego la `V21` (migración anterior pendiente). Fusionar HU #39 antes, o renumerar.
- **Merge con HU #39**: `package.json`, `app.module.ts` y `.env.example` cambian en ambas ramas (conflictos esperados y
  simples). `database.module.ts`, `.gitignore` y el ajuste de `DataConstraintsMigrationTest` son idénticos en las dos.
- `promise-breaker` 7 (dependencia de `amqp-connection-manager`) es solo ESM: Node 22 lo carga sin problema; Jest lo
  transforma (`transformIgnorePatterns`).
