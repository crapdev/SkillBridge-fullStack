# SkillBridge Email Service

Scaffold inicial NestJS compartido para las historias de notificaciones de reservas y verificación de correo.

## Incluye
- NestJS con configuración `.env` validada.
- Endpoint `GET /api/health`.
- `EmailSenderPort` exportado desde `EmailModule`.
- Adaptador temporal `FakeEmailSenderAdapter` para desarrollo paralelo de Persona B.
- Tipos y constantes de eventos basados en el contrato Java actual.
- Estructura inicial `booking-events`.
- Docker Compose local con RabbitMQ y Mailpit.

## Todavía no incluye
- Consumidor RabbitMQ funcional ni topología definitiva.
- Adaptadores Mailpit/Brevo reales.
- ORM, conexión PostgreSQL o migraciones.
- Idempotencia persistente, reintentos y DLQ implementados.
- Lógica de verificación de correo.

Estos puntos dependen de acuerdos entre las personas A/B/C y el equipo Spring Boot.

## Requisitos
Node.js 20+, npm y Docker Compose.

## Instalación
```bash
cp .env.example .env
npm install
docker compose up -d
npm run start:dev
```

URLs locales:
- API health: http://localhost:3000/api/health
- RabbitMQ Management: http://localhost:15672 (skillbridge / skillbridge_dev)
- Mailpit: http://localhost:8025
- SMTP Mailpit: localhost:1025

## División inicial
- Persona A: `EmailSenderPort`, adaptadores Mailpit/Brevo, layout y plantillas.
- Persona B: consumidor RabbitMQ, cola propia/DLQ, routing keys, resolución de destinatarios, idempotencia y reintentos.
- Trabajo conjunto: contratos, persistencia, Docker Compose e integración end-to-end.

## Contrato conocido
Exchange `booking.events` (topic). Routing keys dentro del alcance: `booking.created`, `booking.paid`, `booking.cancelled`, `booking.completed`. `booking.payment-failed` queda fuera del alcance.

El payload Java actual contiene `bookingId`, `offeringId`, `customerId`, `scheduledAt`, `occurredAt`. No contiene `eventId` ni datos de correo/nombre. No cerrar la implementación del consumidor hasta acordar idempotencia y resolución de destinatarios.

El RabbitMQ de este compose es para desarrollo aislado. En la integración se debe conectar al broker existente de SkillBridge y no crear una segunda instancia.
