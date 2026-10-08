# Checklist de regresión manual — SkillBridge

**Subtarea:** SCRUM-47  
**HU padre:** SCRUM-11 — Calidad y entorno: CI reproducible y pruebas estables  
**Épico:** SCRUM-6 — Reservas del cliente  
**Fecha de ejecución prevista:** viernes 9 de octubre de 2026  
**Ejecutado por:** ___________________________  
**Entorno:** Docker Compose local (`docker compose up --build`)

---

## Instrucciones de uso

1. Levanta el entorno antes de empezar: `docker compose up --build -d`
2. Verifica que todos los servicios estén healthy: `docker compose ps`
3. Ejecuta los casos en orden. Cada caso es independiente salvo que se indique lo contrario.
4. Registra el resultado real en la columna **Resultado** y el estado en **Estado**.
5. Si un caso queda **Blocked**, anota el motivo en **Notas** y continúa con el siguiente.
6. No uses credenciales reales ni datos personales en los campos de prueba.

---

## Sección A — Entorno y arranque

### TC-A-01 — El stack levanta correctamente con Docker Compose

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que todos los servicios arrancan y pasan el health check |
| **Precondiciones** | Docker Desktop en ejecución, archivo `.env` configurado a partir de `.env.example` |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar `docker compose up --build -d`
2. Ejecutar `docker compose ps`
3. Verificar que los servicios `backend`, `postgres`, `redis`, `rabbitmq` y `nginx` aparecen como `running` o `healthy`
4. Abrir `http://localhost:8080/actuator/health` en el navegador

**Resultado esperado:** todos los servicios en estado `running`/`healthy`. El endpoint devuelve `{"status":"UP"}`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-A-02 — Las pruebas automatizadas pasan con Maven

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que `mvn clean verify` termina con BUILD SUCCESS y genera el reporte JaCoCo |
| **Precondiciones** | JDK 21, Maven 3.9+ y Docker Desktop en ejecución (Testcontainers necesita Docker) |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Abrir una terminal en la carpeta `backend`
2. Ejecutar `mvn clean verify`
3. Verificar la línea final del output
4. Abrir `backend/target/site/jacoco/index.html` en el navegador

**Resultado esperado:** output termina con `BUILD SUCCESS`. El reporte JaCoCo se abre y muestra cobertura de los paquetes `application.service` e `infrastructure.adapter.out.persistence`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-A-03 — Testcontainers levanta PostgreSQL automáticamente

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que la prueba `JpaOfferingRepositoryTest` corre sin configuración manual de base de datos |
| **Precondiciones** | Docker Desktop en ejecución |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar `mvn -pl backend test -Dtest=JpaOfferingRepositoryTest`
2. Observar el output: debe aparecer una línea de Testcontainers iniciando un contenedor de PostgreSQL
3. Verificar que la prueba termina en verde

**Resultado esperado:** la prueba pasa. En el log aparece algo como `Creating container for image: postgres:17-alpine`. No se requiere PostgreSQL instalado localmente.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

## Sección B — Autenticación

### TC-B-01 — Registro de usuario nuevo

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un usuario puede registrarse y recibe un JWT |
| **Precondiciones** | Stack levantado y healthy |
| **Datos de prueba** | `name`: "Test User", `email`: "testuser@example.com", `password`: "Password123" |

**Pasos:**
1. Ejecutar:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"testuser@example.com","password":"Password123"}'
```
2. Revisar el cuerpo de la respuesta

**Resultado esperado:** HTTP 200 o 201. Cuerpo contiene `token` y `tokenType: "Bearer"`. El token tiene formato JWT (tres partes separadas por `.`).

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-B-02 — Login con credenciales correctas

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un usuario registrado puede iniciar sesión y obtener un JWT |
| **Precondiciones** | TC-B-01 ejecutado con éxito |
| **Datos de prueba** | `email`: "testuser@example.com", `password`: "Password123" |

**Pasos:**
1. Ejecutar:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"testuser@example.com","password":"Password123"}'
```
2. Guardar el valor del campo `token` para los casos siguientes

**Resultado esperado:** HTTP 200. Cuerpo contiene `token` y `tokenType: "Bearer"`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-B-03 — Petición con token inválido devuelve 401

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un token malformado es rechazado con 401 |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Token inválido: `esto.no.es.un.jwt` |

**Pasos:**
1. Ejecutar:
```bash
curl -i -X GET http://localhost:8080/api/bookings \
  -H "Authorization: Bearer esto.no.es.un.jwt"
```
2. Revisar el código de estado HTTP y el cuerpo de la respuesta

**Resultado esperado:** HTTP 401. El cuerpo no contiene el token enviado ni el valor del header `Authorization`. En los logs del backend (`docker compose logs backend`) debe aparecer una línea DEBUG con el tipo de excepción pero sin el token.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-B-04 — Petición sin token devuelve 401

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un endpoint protegido rechaza peticiones sin Authorization |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar:
```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"offeringId":"11111111-1111-1111-1111-111111111111","scheduledAt":"2030-10-10T15:00:00Z"}'
```
2. Revisar el código de estado HTTP

**Resultado esperado:** HTTP 401. No se crea ninguna reserva.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

## Sección C — Catálogo público

### TC-C-01 — Consultar catálogo sin autenticación

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que el catálogo es accesible sin token |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar: `curl http://localhost:8080/api/offerings`
2. Ejecutar la misma petición una segunda vez

**Resultado esperado:** HTTP 200 en ambas llamadas. La respuesta contiene al menos 3 ofertas activas. La segunda llamada puede resolverse desde Redis (verificable con `docker compose exec redis redis-cli KEYS \*`).

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

## Sección D — Reservas del cliente (SCRUM-6)

### TC-D-01 — Crear reserva con fecha futura válida

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar el flujo completo de creación de reserva |
| **Precondiciones** | TC-B-02 ejecutado; JWT guardado en variable `TOKEN` |
| **Datos de prueba** | `offeringId`: `11111111-1111-1111-1111-111111111111`, `scheduledAt`: `2030-12-01T10:00:00Z` |

**Pasos:**
1. Ejecutar:
```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"offeringId":"11111111-1111-1111-1111-111111111111","scheduledAt":"2030-12-01T10:00:00Z"}'
```
2. Revisar el código de estado y el cuerpo
3. Revisar logs: `docker compose logs -f backend`
4. Revisar RabbitMQ: `http://localhost:15672` (guest/guest) → `booking.created.queue`

**Resultado esperado:** HTTP 201 o 200. La reserva aparece en la respuesta con un `id` generado. En los logs del backend aparece la línea de notificación asíncrona. En RabbitMQ el mensaje fue consumido.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-D-02 — Crear reserva con fecha pasada es rechazada

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que la regla de negocio de fecha futura se aplica |
| **Precondiciones** | TC-B-02 ejecutado; JWT guardado en variable `TOKEN` |
| **Datos de prueba** | `scheduledAt`: `2020-01-01T10:00:00Z` (fecha pasada) |

**Pasos:**
1. Ejecutar:
```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"offeringId":"11111111-1111-1111-1111-111111111111","scheduledAt":"2020-01-01T10:00:00Z"}'
```
2. Revisar el código de estado y el cuerpo de la respuesta

**Resultado esperado:** HTTP 422. El cuerpo contiene un mensaje que indica que la reserva debe programarse en una fecha futura. No se crea ninguna reserva.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-D-03 — Crear reserva con oferta inexistente es rechazada

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que se valida la existencia de la oferta |
| **Precondiciones** | TC-B-02 ejecutado; JWT guardado en variable `TOKEN` |
| **Datos de prueba** | `offeringId`: `00000000-0000-0000-0000-000000000000` (UUID que no existe) |

**Pasos:**
1. Ejecutar:
```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"offeringId":"00000000-0000-0000-0000-000000000000","scheduledAt":"2030-12-01T10:00:00Z"}'
```
2. Revisar el código de estado y el mensaje

**Resultado esperado:** HTTP 404. El mensaje indica que el servicio no fue encontrado. No se crea ninguna reserva.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

## Sección E — Instalación reproducible del frontend

### TC-E-01 — El frontend compila con `npm ci`

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que la instalación es reproducible usando `npm ci` y `package-lock.json` |
| **Precondiciones** | Node 22 instalado, `frontend/package-lock.json` commiteado en el repositorio |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Abrir terminal en la carpeta `frontend`
2. Eliminar `node_modules` si existe: `Remove-Item -Recurse -Force node_modules`
3. Ejecutar: `npm ci`
4. Ejecutar: `npm run build`
5. Verificar que no hay errores

**Resultado esperado:** `npm ci` instala exactamente las versiones del `package-lock.json` sin modificarlo. `npm run build` termina sin errores.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

### TC-E-02 — El CI usa `npm ci` (hallazgo SCRUM-11)

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar si el workflow de CI usa `npm ci` o `npm install` |
| **Precondiciones** | Acceso al archivo `.github/workflows/ci.yml` |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Abrir `.github/workflows/ci.yml`
2. Buscar el paso de instalación del frontend
3. Verificar si dice `npm ci` o `npm install`

**Resultado esperado:** el paso dice `npm ci`. Si dice `npm install`, es un hallazgo pendiente de corrección (reportado en `jacoco-coverage-report.md` sección 5).

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| | ☐ Pass ☐ Fail ☐ Blocked | |

---

## Resumen de ejecución

| Sección | Total casos | Pass | Fail | Blocked |
|---|---|---|---|---|
| A — Entorno y arranque | 3 | | | |
| B — Autenticación | 4 | | | |
| C — Catálogo público | 1 | | | |
| D — Reservas del cliente | 3 | | | |
| E — Instalación reproducible | 2 | | | |
| **Total** | **13** | | | |

---

## Observaciones generales

_(Espacio para notas del ejecutor)_

---

## Relación con criterios de SCRUM-11

| Criterio de aceptación | Casos que lo verifican |
|---|---|
| Pruebas con Testcontainers corren con Docker reciente | TC-A-02, TC-A-03 |
| Frontend se instala de forma reproducible con `npm ci` | TC-E-01, TC-E-02 |
| Hay un reporte de cobertura revisado | Ver `jacoco-coverage-report.md` |
| Hay un checklist de regresión manual | Este documento |
