# Checklist de regresión manual — SkillBridge

**Subtarea:** SCRUM-47  
**HU padre:** SCRUM-11 — Calidad y entorno: CI reproducible y pruebas estables  
**Épico:** SCRUM-6 — Reservas del cliente  
**Fecha de ejecución prevista:** viernes 9 de octubre de 2026  
**Ejecutado por:** Carlos Andrés Ospina  
**Fecha de ejecución real:** 2026-10-09  
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
| Todos los servicios healthy. `actuator/health` devuelve `{"estado":"UP"}` | PASS | Ejecutado 2026-10-09 |

---

### TC-A-02 — Las pruebas automatizadas pasan con Maven

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que `mvn clean verify` termina con BUILD SUCCESS y genera el reporte JaCoCo |
| **Precondiciones** | JDK 21, Maven 3.9+ y Docker Desktop en ejecución |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Abrir una terminal en la carpeta `backend`
2. Ejecutar `mvn clean verify`
3. Verificar la línea final del output
4. Abrir `backend/target/site/jacoco/index.html` en el navegador

**Resultado esperado:** output termina con `BUILD SUCCESS`. El reporte JaCoCo muestra cobertura.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| No ejecutado — Maven no disponible en el entorno | BLOCKED | Ejecutar manualmente con `mvn clean verify` en carpeta `backend` |

---

### TC-A-03 — Testcontainers levanta PostgreSQL automáticamente

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que `JpaOfferingRepositoryTest` corre sin PostgreSQL instalado localmente |
| **Precondiciones** | Docker Desktop en ejecución |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar `mvn -pl backend test -Dtest=JpaOfferingRepositoryTest`
2. Observar que Testcontainers inicia un contenedor de PostgreSQL automáticamente
3. Verificar que la prueba termina en verde

**Resultado esperado:** prueba pasa. En el log aparece `Creating container for image: postgres:17-alpine`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| No ejecutado — Maven no disponible en el entorno | BLOCKED | Ejecutar manualmente en IntelliJ o con `mvn test` |

---

## Sección B — Autenticación

### TC-B-01 — Registro de usuario nuevo

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un usuario puede registrarse y recibe un JWT |
| **Precondiciones** | Stack levantado y healthy |
| **Datos de prueba** | `name`: "Test User", `email`: "testuser@example.com", `password`: "Password123" |

**Pasos:**
1. Ejecutar POST a `http://localhost:8080/api/auth/register` con los datos de prueba
2. Revisar el cuerpo de la respuesta

**Resultado esperado:** HTTP 200/201. Cuerpo contiene `token` y `tokenType: "Bearer"`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 200. Respuesta contiene `token` (JWT de 3 partes) y `tokenType: Bearer` | PASS | Ejecutado 2026-10-09 |

---

### TC-B-02 — Login con credenciales correctas

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un usuario registrado puede iniciar sesión y obtener un JWT |
| **Precondiciones** | TC-B-01 ejecutado con éxito |
| **Datos de prueba** | `email`: "testuser@example.com", `password`: "Password123" |

**Pasos:**
1. Ejecutar POST a `http://localhost:8080/api/auth/login` con los datos de prueba
2. Guardar el valor del campo `token` para los casos siguientes

**Resultado esperado:** HTTP 200. Cuerpo contiene `token` y `tokenType: "Bearer"`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 200. Token JWT recibido correctamente | PASS | Ejecutado 2026-10-09 |

---

### TC-B-03 — Petición con token inválido

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un token malformado es rechazado |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Token inválido: `esto.no.es.un.jwt` |

**Pasos:**
1. Enviar GET a `http://localhost:8080/api/bookings` con `Authorization: Bearer esto.no.es.un.jwt`
2. Revisar el código de estado HTTP

**Resultado esperado:** HTTP 401. El cuerpo no contiene el token enviado.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 403 en vez de 401. La peticion fue rechazada correctamente pero el codigo no es el esperado | FAIL | Hallazgo: Spring Security devuelve 403 en lugar de 401. Corresponde a la subtarea de respuesta HTTP de la HU |

---

### TC-B-04 — Petición sin token devuelve 401

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que un endpoint protegido rechaza peticiones sin Authorization |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Enviar POST a `http://localhost:8080/api/bookings` sin header Authorization
2. Revisar el código de estado HTTP

**Resultado esperado:** HTTP 401. No se crea ninguna reserva.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 403 en vez de 401. La reserva no se creó correctamente | FAIL | Mismo hallazgo que TC-B-03. Pendiente de correccion en subtarea de respuesta HTTP |

---

## Sección C — Catálogo público

### TC-C-01 — Consultar catálogo sin autenticación

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que el catálogo es accesible sin token |
| **Precondiciones** | Stack levantado |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ejecutar GET a `http://localhost:8080/api/offerings`
2. Ejecutar la misma petición una segunda vez

**Resultado esperado:** HTTP 200. La respuesta contiene al menos 3 ofertas activas.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 200. Respuesta contiene 3 ofertas activas: Mentoria Java Backend, Mentoria Angular, Diseño de Arquitectura Cloud | PASS | Ejecutado 2026-10-09 |

---

## Sección D — Reservas del cliente (SCRUM-6)

### TC-D-01 — Crear reserva con fecha futura válida

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar el flujo completo de creación de reserva |
| **Precondiciones** | TC-B-02 ejecutado; JWT válido disponible |
| **Datos de prueba** | `offeringId`: `11111111-1111-1111-1111-111111111111`, `scheduledAt`: `2030-12-01T10:00:00Z` |

**Pasos:**
1. Enviar POST a `http://localhost:8080/api/bookings` con JWT y datos de prueba
2. Revisar el código de estado y el cuerpo
3. Revisar logs del backend

**Resultado esperado:** HTTP 200/201. Reserva creada con `id` generado y `status: CREATED`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 200. Reserva creada con id `78f2215e-8f68-4b0c-b615-caa15ae5f05c` y status `CREATED` | PASS | Ejecutado 2026-10-09 |

---

### TC-D-02 — Crear reserva con fecha pasada es rechazada

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que la regla de negocio de fecha futura se aplica |
| **Precondiciones** | TC-B-02 ejecutado; JWT válido disponible |
| **Datos de prueba** | `scheduledAt`: `2020-01-01T10:00:00Z` (fecha pasada) |

**Pasos:**
1. Enviar POST a `http://localhost:8080/api/bookings` con JWT y fecha pasada
2. Revisar el código de estado y el cuerpo de la respuesta

**Resultado esperado:** HTTP 422. El cuerpo indica que la reserva debe ser en fecha futura.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 400 en vez de 422. La reserva fue rechazada correctamente pero el codigo HTTP no es el esperado | FAIL | Hallazgo: GlobalExceptionHandler no mapea BusinessRuleException a 422. Reportar al equipo |

---

### TC-D-03 — Crear reserva con oferta inexistente es rechazada

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que se valida la existencia de la oferta |
| **Precondiciones** | TC-B-02 ejecutado; JWT válido disponible |
| **Datos de prueba** | `offeringId`: `00000000-0000-0000-0000-000000000000` |

**Pasos:**
1. Enviar POST a `http://localhost:8080/api/bookings` con JWT y UUID inexistente
2. Revisar el código de estado y el mensaje

**Resultado esperado:** HTTP 404. El mensaje indica que el servicio no fue encontrado.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| HTTP 404. La oferta inexistente fue rechazada correctamente | PASS | Ejecutado 2026-10-09 |

---

## Sección E — Instalación reproducible del frontend

### TC-E-01 — El frontend compila con `npm ci`

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que la instalación es reproducible usando `npm ci` |
| **Precondiciones** | Node 22 instalado, `frontend/package-lock.json` commiteado |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Ir a la carpeta `frontend`
2. Eliminar `node_modules` si existe
3. Ejecutar `npm ci` y luego `npm run build`

**Resultado esperado:** `npm ci` instala sin modificar `package-lock.json`. Build termina sin errores.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| No ejecutado — Node no disponible en el entorno de Kiro | BLOCKED | Ejecutar manualmente en la carpeta `frontend` |

---

### TC-E-02 — El CI usa `npm ci`

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar si el workflow de CI usa `npm ci` o `npm install` |
| **Precondiciones** | Acceso al archivo `.github/workflows/ci.yml` |
| **Datos de prueba** | Ninguno |

**Pasos:**
1. Abrir `.github/workflows/ci.yml`
2. Buscar el paso de instalación del frontend

**Resultado esperado:** el paso dice `npm ci`.

| Resultado real | Estado | Notas / Evidencia |
|---|---|---|
| El CI usa `npm install --no-audit --no-fund` en vez de `npm ci` | FAIL | Hallazgo reportado en jacoco-coverage-report.md seccion 5. Pendiente de correccion |

---

## Resumen de ejecución

| Sección | Total casos | Pass | Fail | Blocked |
|---|---|---|---|---|
| A — Entorno y arranque | 3 | 1 | 0 | 2 |
| B — Autenticación | 4 | 2 | 2 | 0 |
| C — Catálogo público | 1 | 1 | 0 | 0 |
| D — Reservas del cliente | 3 | 2 | 1 | 0 |
| E — Instalación reproducible | 2 | 0 | 1 | 1 |
| **Total** | **13** | **6** | **4** | **3** |

---

## Hallazgos encontrados

| ID | Descripción | Impacto | Acción |
|---|---|---|---|
| H-01 | Token inválido y petición sin token devuelven 403 en vez de 401 | Medio — el rechazo funciona pero el código HTTP no es el correcto | Reportar al responsable de la subtarea de respuesta HTTP de la HU |
| H-02 | Reserva con fecha pasada devuelve 400 en vez de 422 | Medio — la regla de negocio funciona pero el GlobalExceptionHandler no mapea correctamente | Reportar al equipo para corrección en GlobalExceptionHandler |
| H-03 | CI usa `npm install` en vez de `npm ci` | Bajo — builds pueden no ser reproducibles entre ejecuciones | Corregir `.github/workflows/ci.yml` (reportado también en jacoco-coverage-report.md) |

---

## Relación con criterios de SCRUM-11

| Criterio de aceptación | Casos que lo verifican | Estado |
|---|---|---|
| Pruebas con Testcontainers corren con Docker reciente | TC-A-02, TC-A-03 | BLOCKED — ejecutar con `mvn clean verify` |
| Frontend se instala de forma reproducible con `npm ci` | TC-E-01, TC-E-02 | FAIL — CI usa `npm install` |
| Hay un reporte de cobertura revisado | Ver `jacoco-coverage-report.md` | Completado |
| Hay un checklist de regresión manual | Este documento | Completado |
