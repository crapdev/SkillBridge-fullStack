# Informe de cobertura JaCoCo — SkillBridge Backend

**Subtarea:** SCRUM-46  
**HU padre:** SCRUM-11 — Calidad y entorno: CI reproducible y pruebas estables  
**Épico:** SCRUM-6 — Reservas del cliente  
**Fecha de análisis:** 2026-10-06  
**Analista:** Carlos Andrés Ospina  
**Rama analizada:** `test`

---

## 1. Cómo se genera el reporte

El proyecto usa JaCoCo configurado en `backend/pom.xml` con las fases `prepare-agent` y `report`.  
El reporte HTML se genera ejecutando:

```bash
cd backend
mvn clean verify
```

Ruta del reporte:

```
backend/target/site/jacoco/index.html
```

> **Bloqueo de entorno:** Maven no está disponible en el PATH del entorno de Kiro (PowerShell devuelve `CommandNotFoundException`). Por esta razón el reporte HTML no pudo generarse en este entorno. El análisis de huecos que sigue se basa en la inspección estática del código fuente y las pruebas existentes en la rama `test`. Los porcentajes exactos deberán verificarse ejecutando `mvn clean verify` localmente o en el CI.

---

## 2. Pruebas existentes en la rama `test`

| Archivo de prueba | Clase bajo prueba | Qué verifica |
|---|---|---|
| `BookingServiceTest` | `BookingService` | Flujo feliz: crea reserva, persiste y publica evento |
| `OfferingServiceTest` | `OfferingService` | Cache hit: no consulta la base de datos cuando hay caché |
| `JpaOfferingRepositoryTest` | `JpaOfferingRepository` | Flyway siembra 3 ofertas activas (requiere Docker) |

---

## 3. Mapa de cobertura por paquete

### 3.1 `application.service` — Cobertura parcial ⚠️

#### `BookingService`

| Ruta de código | Cubierta | Prueba existente |
|---|---|---|
| Flujo feliz: oferta activa, fecha futura, usuario existe | ✅ | `shouldPersistAndPublishBookingCreated` |
| Reserva en fecha pasada → `BusinessRuleException` | ❌ | Ninguna |
| Oferta no encontrada → `DomainNotFoundException` | ❌ | Ninguna |
| Oferta inactiva → `BusinessRuleException` | ❌ | Ninguna |
| Usuario no encontrado → `DomainNotFoundException` | ❌ | Ninguna |

**Riesgo:** las cuatro reglas de negocio sin prueba son críticas para el épico SCRUM-6. Un cambio accidental en la validación de fecha o en la verificación de estado de oferta pasaría desapercibido.

#### `OfferingService`

| Ruta de código | Cubierta | Prueba existente |
|---|---|---|
| Cache hit: devuelve lista desde caché | ✅ | `shouldReturnCacheWithoutQueryingDatabase` |
| Cache miss: consulta PostgreSQL y guarda en caché | ❌ | Ninguna |

**Riesgo:** el camino a la base de datos no está probado. Si `putActiveOfferings` falla silenciosamente, el sistema consulta PostgreSQL en cada petición sin que ninguna prueba lo detecte.

---

### 3.2 `infrastructure.adapter.out.persistence` — Cobertura parcial ⚠️

| Clase | Cubierta | Observación |
|---|---|---|
| `JpaOfferingRepository` (via `JpaOfferingRepositoryTest`) | ✅ parcial | Solo verifica el conteo de semillas. No prueba queries por estado ni por ID |
| `BookingPersistenceAdapter` | ❌ | Sin ninguna prueba |
| `OfferingPersistenceAdapter` | ❌ | Sin ninguna prueba |
| `UserPersistenceAdapter` | ❌ | Sin ninguna prueba |

---

### 3.3 `infrastructure.adapter.in.rest` — Sin cobertura ❌

| Clase | Cubierta | Observación |
|---|---|---|
| `BookingController` | ❌ | Sin prueba |
| `AuthController` | ❌ | Sin prueba |
| `OfferingController` | ❌ | Sin prueba |
| `AiController` | ❌ | Sin prueba |
| `GlobalExceptionHandler` | ❌ | Sin prueba; maneja todos los errores HTTP del proyecto |

**Riesgo:** el `GlobalExceptionHandler` no está probado. Si el mapeo de excepciones está mal, los clientes recibirían respuestas de error inconsistentes o con datos internos expuestos.

---

### 3.4 `infrastructure.security` — Sin cobertura ❌

| Clase | Cubierta | Observación |
|---|---|---|
| `JwtAuthenticationFilter` | ❌ | SCRUM-42 asignado a otro integrante |
| `JwtService` | ❌ | Generación y validación de tokens sin prueba |
| `DatabaseUserDetailsService` | ❌ | Sin prueba |

---

### 3.5 `domain` — Sin cobertura directa ℹ️

| Clase | Cubierta indirectamente | Observación |
|---|---|---|
| `Booking` (record) | ✅ via `BookingServiceTest` | |
| `Offering` (record) | ✅ via ambos service tests | |
| `BookingStatus` (enum) | ✅ parcial | Solo `CREATED` usado en pruebas |
| `BusinessRuleException` | ❌ | Solo instanciada en producción, nunca en pruebas |
| `DomainNotFoundException` | ❌ | Solo instanciada en producción, nunca en pruebas |

---

## 4. Huecos prioritarios

| Prioridad | Clase | Comportamiento sin prueba | Sugerencia |
|---|---|---|---|
| 🔴 Alta | `BookingService` | Reserva en fecha pasada | `shouldRejectBookingInThePast` |
| 🔴 Alta | `BookingService` | Oferta no encontrada | `shouldThrowWhenOfferingNotFound` |
| 🔴 Alta | `BookingService` | Oferta inactiva | `shouldRejectInactiveOffering` |
| 🔴 Alta | `BookingService` | Usuario no encontrado | `shouldThrowWhenUserNotFound` |
| 🟡 Media | `OfferingService` | Cache miss: consulta DB y guarda en caché | `shouldQueryDatabaseOnCacheMiss` |
| 🟡 Media | `GlobalExceptionHandler` | Mapeo de `BusinessRuleException` a 422 | Prueba con `@WebMvcTest` |
| 🟡 Media | `JwtService` | Generación y validación de token | Test unitario puro |
| 🟢 Baja | `BookingPersistenceAdapter` | Guardar y recuperar reserva | Test con Testcontainers |
| 🟢 Baja | `JpaOfferingRepository` | Consulta por ID | Ampliar `JpaOfferingRepositoryTest` |

---

## 5. Hallazgo de CI — instalación no reproducible

El archivo `.github/workflows/ci.yml` usa `npm install` en el job del frontend:

```yaml
- name: Install
  run: npm install --no-audit --no-fund
```

El criterio de aceptación de SCRUM-11 requiere instalación reproducible con `package-lock.json` y `npm ci`. Con `npm install`, si no hay `package-lock.json` en el repositorio o las versiones tienen rangos abiertos (`^`, `~`), el build del CI puede diferir entre ejecuciones.

**Acción recomendada** (corresponde al criterio de SCRUM-11, no a SCRUM-46):
```yaml
- name: Install
  run: npm ci --no-audit --no-fund
```
Verificar también que `frontend/package-lock.json` esté commiteado en el repositorio.

---

## 6. Cómo verificar este informe localmente

```bash
# Requiere JDK 21, Maven 3.9+ y Docker Desktop en ejecución
cd backend
mvn clean verify
# Abrir en el navegador:
open target/site/jacoco/index.html   # macOS
start target/site/jacoco/index.html  # Windows
```

La prueba `JpaOfferingRepositoryTest` requiere Docker en ejecución (Testcontainers levanta PostgreSQL automáticamente).

---

## 7. Relación con criterios de SCRUM-11

| Criterio de aceptación | Estado |
|---|---|
| Pruebas con Testcontainers corren en las máquinas del equipo con Docker reciente | ✅ `JpaOfferingRepositoryTest` usa Testcontainers 1.21.4 (SCRUM-44) |
| Frontend se instala de forma reproducible con `package-lock.json` y `npm ci` | ⚠️ CI usa `npm install` — hallazgo reportado en sección 5 |
| Hay un reporte de cobertura revisado | ✅ Este documento |
| Hay un checklist de regresión manual | Ver `regression-checklist.md` (SCRUM-47) |
