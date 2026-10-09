# Panel de administración — guía para el backend

El frontend del panel de administración (HU-14) ya está construido. Este documento explica **qué necesita del backend** para mostrar datos reales y **qué falta trabajar después** para que quede completamente funcional.

El panel permite a un usuario con rol `ADMIN` gestionar **proveedores** (`PROVIDER`) y **clientes** (`CUSTOMER`): ver indicadores, listar, buscar, crear, editar y (más adelante) desactivar/reactivar cuentas.

---

## 1. Estado actual

| Parte | Estado |
|---|---|
| Vistas del panel (dashboard, listados, formulario, modal de desactivar) | ✅ Hecho |
| Protección de rutas en el front (solo `ADMIN` entra a `/admin`) | ✅ Hecho |
| Usuario administrador inicial (migración `V5__seed_admin.sql`) | ✅ Hecho |
| Endpoints `/api/admin/users/**` | ❌ **Pendiente — Fase 1** |
| Activar / desactivar cuentas | ❌ **Pendiente — Fase 2 (requiere cambio en la BD)** |
| Especialidad del proveedor, nombre del admin, política de contraseña | ❌ **Pendiente — Fase 3** |

Mientras los endpoints no existan, el backend responde `404` y el panel muestra el aviso *"Esta función aún no está disponible en el servidor"* en lugar de los datos.

### ¿Qué muestra el panel y de dónde sale cada dato?

| Dato en pantalla | Origen | ¿Existe hoy en la BD? |
|---|---|---|
| Total de proveedores / clientes | `COUNT` de `app_users` por `role` | ✅ |
| "+N este mes" | `app_users.created_at` | ✅ |
| Nombre, email, rol | `app_users.name`, `email`, `role` | ✅ |
| Fecha de creación / "Hace 2 horas" | `app_users.created_at` | ✅ |
| Activos / Inactivos, filtro por estado, botón desactivar | `app_users.active` | ❌ Fase 2 |
| Especialidad del proveedor | categorías de sus servicios (`offerings.provider_id`) | ❌ Fase 3 |
| Nombre del admin en el menú lateral | claim `name` en el JWT o endpoint `/me` | ❌ Fase 3 |

---

## 2. Cómo ver el panel

1. Levanta el backend con Docker y el front en modo desarrollo:
   ```bash
   cd frontend && npm start
   ```
2. Entra a http://localhost:4200/login con la cuenta de administrador (correo `calborparra@gmail.com`; la contraseña te la comparte Cristian, no se guarda en el repositorio).
3. Al iniciar sesión, un `ADMIN` llega directo a `/admin`.

**Datos simulados (solo `ng serve`):** en la barra superior del panel hay un botón **"Datos simulados: ON/OFF"**. Con ON, el front responde los endpoints con datos de ejemplo en memoria siguiendo exactamente el contrato de este documento; sirve como referencia de qué debe devolver cada endpoint. En un build de producción (Docker, puerto 8088) el simulador no existe.

Código de referencia del simulador: `frontend/src/app/features/admin/data/admin-mock.interceptor.ts`.

---

## 3. Fase 1 — Endpoints con lo que ya existe en la BD

Esta fase **no requiere cambios en la base de datos**. Todo sale de la tabla `app_users`.

### 3.1 Seguridad

Todo `/api/admin/**` debe ser exclusivo del rol `ADMIN`. Hoy cae en `.anyRequest().authenticated()`, es decir, cualquier usuario con sesión podría usarlo. Agregar en `SecurityConfiguration`, junto a las demás reglas:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Un usuario sin rol `ADMIN` debe recibir `403`. El guard del front es solo de experiencia de usuario; **la autorización real es la del backend**.

### 3.2 Contrato

Base: `/api/admin/users` · Autenticación: `Bearer JWT` con rol `ADMIN` · Errores en formato `ProblemDetail` (como el resto de la API, con el mensaje en `detail`).

Estos endpoints solo gestionan cuentas `PROVIDER` y `CUSTOMER`. **Nunca** deben listar, devolver ni modificar cuentas `ADMIN` (si piden una por id, responder `404`).

#### Objeto `AdminUser` (respuesta)

```json
{
  "id": "2f6c1f4e-...",
  "name": "Ana Pérez",
  "email": "ana.perez@cloudexpert.co",
  "role": "PROVIDER",
  "createdAt": "2026-10-09T15:16:45.722Z"
}
```

- **Nunca** incluir `password` ni el hash.
- `createdAt` en ISO-8601 (`Instant`).
- `active` se agrega en la Fase 2. Mientras no venga, el front asume que la cuenta está activa.

#### `GET /api/admin/users` — listado paginado

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `role` | Sí | `PROVIDER` o `CUSTOMER` |
| `page` | No (0) | Página, base 0 |
| `size` | No (10) | Tamaño de página (el front usa 10 y 5) |
| `sort` | No | El front envía `createdAt,desc` (más recientes primero) |
| `q` | No | Busca en `name` **o** `email`, sin distinguir mayúsculas, coincidencia parcial |
| `active` | No | `true`/`false`. **Fase 2**; mientras tanto se puede ignorar |

Respuesta `200` con la forma de `PagedModel` de Spring:

```json
{
  "content": [ { "id": "...", "name": "...", "email": "...", "role": "PROVIDER", "createdAt": "..." } ],
  "page": { "size": 10, "number": 0, "totalElements": 42, "totalPages": 5 }
}
```

Para obtener exactamente esa forma con Spring Data basta con devolver `Page<T>` y activar en una clase `@Configuration`:

```java
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
```

#### `GET /api/admin/users/stats` — indicadores

```json
{
  "providers": { "total": 42, "newThisMonth": 3 },
  "customers": { "total": 120, "newThisMonth": 11 }
}
```

- `newThisMonth`: cuentas con `created_at` desde el día 1 del mes actual. Usar la zona horaria del negocio (`America/Bogota`) para definir el inicio del mes.
- En la Fase 2 se agregan `active` e `inactive` a cada rol.

#### `GET /api/admin/users/{id}` — detalle

`200` con un `AdminUser`. `404` si no existe o si es una cuenta `ADMIN`.

#### `POST /api/admin/users` — crear

```json
{ "name": "Mariana Silva", "email": "mariana@dominio.com", "password": "********", "role": "PROVIDER" }
```

| Campo | Validación (las mismas de `RegisterRequest`) |
|---|---|
| `name` | Obligatorio, máx. 120 |
| `email` | Obligatorio, email válido, máx. 180. Guardar con `trim()` y en minúsculas |
| `password` | Obligatorio, 8–72 caracteres. Guardar con BCrypt (`PasswordHasherPort`) |
| `role` | Obligatorio, solo `PROVIDER` o `CUSTOMER` (rechazar `ADMIN` con `400`) |

Respuestas:
- `201` con el `AdminUser` creado.
- `422` si el correo ya existe, con `detail: "El correo ya está registrado"` (lanzar `BusinessRuleException`, como en `AuthService.register`). **El front muestra este error debajo del campo de correo**, por eso es importante que sea `422`.
- `400` si falla una validación.

#### `PUT /api/admin/users/{id}` — editar

```json
{ "name": "Mariana Silva", "email": "mariana@dominio.com", "role": "CUSTOMER" }
```

- Mismas validaciones que al crear, **sin** `password`: la contraseña no se cambia desde el panel.
- Se permite cambiar el rol entre `PROVIDER` y `CUSTOMER`.
- **Conservar `created_at`** al actualizar (ojo: el mismo problema ya ocurrió con las reservas en SCRUM-9).
- `200` con el `AdminUser` actualizado · `404` si no existe o es `ADMIN` · `422` si el correo pertenece a **otra** cuenta · `400` por validación.

> No hay endpoint para eliminar. Las cuentas nunca se borran: se desactivan (Fase 2). Además, borrar un cliente con reservas fallaría por la llave foránea `bookings.customer_id`.

### 3.3 Notas de implementación

- **`createdAt` no llega hoy al dominio.** `UserAccount` no tiene `createdAt` y `UserEntity` no tiene getter para él. Hay que agregarlo al record de dominio y al mapeo en `UserPersistenceAdapter`. No requiere cambios en la BD.
- **Arquitectura hexagonal** (la valida `HexagonalArchitectureTest`): casos de uso en `application/port/in`, consultas nuevas en `UserRepositoryPort`, controlador en `infrastructure/adapter/in/rest` (por ejemplo `AdminUserController`) y DTOs en `.../rest/dto`.
- Consultas sugeridas en `JpaUserRepository`: `countByRole`, `countByRoleAndCreatedAtGreaterThanEqual`, y una búsqueda paginada por `role` + `name`/`email` (`ContainingIgnoreCase`) o una consulta con `@Query`.
- Agregar pruebas: acceso con rol `CUSTOMER`/`PROVIDER` → `403`, correo duplicado → `422`, `created_at` se conserva al editar, nunca se exponen cuentas `ADMIN`.
- Documentar los endpoints en `docs/API.md` al terminar.

---

## 4. Fase 2 — Estados de cuenta (activo / inactivo)

**Requiere un cambio en la base de datos.** Es lo que habilita los indicadores Activos/Inactivos, el filtro por estado y el modal de desactivar/reactivar, que en el front ya existen pero están deshabilitados.

### 4.1 Base de datos

Nueva migración de Flyway (coordinar el número de versión con el equipo, ver sección 6):

```sql
ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_app_users_role_active ON app_users(role, active);
```

Todas las cuentas existentes quedan activas, que es su comportamiento actual.

### 4.2 Backend

1. Agregar `active` a `UserEntity`, `UserAccount` y a la respuesta `AdminUser`.
2. Aplicar el filtro `active` en `GET /api/admin/users`.
3. Agregar `active` e `inactive` por rol en `GET /api/admin/users/stats`:
   ```json
   { "providers": { "total": 42, "newThisMonth": 3, "active": 38, "inactive": 4 }, "customers": { ... } }
   ```
4. Nuevo endpoint para cambiar el estado:
   ```
   PATCH /api/admin/users/{id}/status
   { "active": false }
   ```
   Responde `200` con el `AdminUser` actualizado, o `404` si no existe o es `ADMIN`.
5. **Una cuenta inactiva no puede iniciar sesión:**
   - En `AuthService.login`, rechazar si `active = false` con un mensaje claro (por ejemplo *"La cuenta está desactivada"*).
   - En `DatabaseUserDetailsService`, construir el usuario con `.disabled(!active)` para que los tokens ya emitidos dejen de funcionar en la siguiente petición.
6. Desactivar **conserva todo el historial** (reservas, servicios). No se borra nada.
7. Pruebas: login de una cuenta inactiva rechazado, token de una cuenta desactivada deja de servir, reactivar devuelve el acceso.

### 4.3 Frontend (cuando la Fase 2 esté lista)

En `frontend/src/app/features/admin/admin.config.ts` cambiar:

```ts
export const USER_STATUS_ENABLED = true;
```

Con eso se habilitan los botones, el filtro y los indicadores reales. Las vistas no necesitan más cambios.

---

## 5. Fase 3 — Mejoras posteriores

| Mejora | Qué hace falta | Cambio en el front |
|---|---|---|
| **Especialidad del proveedor** (columna del mockup: "Cloud Architecture", "Frontend Angular"…) | La rama HU-08 agrega `offerings.provider_id`. Con eso, devolver en `AdminUser` un campo `specialties: string[]` con las categorías distintas de los servicios del proveedor | Agregar la columna "Especialidad" en el listado y en el dashboard |
| **Nombre del admin en el menú lateral** (hoy se muestra su email, porque el JWT solo trae `sub` = email y `role`) | Agregar el claim `name` al token en `JwtService.generate`, o crear un endpoint `GET /api/users/me` | Mostrar el nombre y sus iniciales en lugar del email |
| **Contraseña con mayúsculas y números** (como pide el mockup; hoy solo se valida la longitud 8–72) | Decidirlo con el PO. Si se aprueba, validarlo en el backend (registro y alta desde el panel) | Agregar la misma regla y el texto de ayuda en el formulario |
| **Cambio de rol de un proveedor con servicios** (cuando exista `offerings.provider_id`) | Definir qué pasa con sus servicios si pasa a cliente: impedirlo con `422` o desactivarlos | Mostrar el error que devuelva el backend (ya está soportado) |

---

## 6. Administrador inicial y Flyway

- `backend/src/main/resources/db/migration/V5__seed_admin.sql` crea el administrador si no existe. Si el correo ya estaba registrado (como `CUSTOMER`), solo lo promueve a `ADMIN` y conserva su nombre y contraseña.
- Se usó la versión **V5** porque V2 (HU-08) y V3 (HU-15) ya están tomadas en otras ramas. Las nuevas migraciones del panel (Fase 2) deben usar el siguiente número libre al momento de integrarlas.
- **Bases de datos locales que ya aplicaron V5** antes que V2/V3: al integrar esas ramas, Flyway rechazará las migraciones "anteriores". Opciones: recrear el volumen de Postgres (`docker compose down -v`) o activar `spring.flyway.out-of-order: true`. En una base nueva no hay problema.

---

## 7. Checklist para dar el panel por terminado

**Fase 1**
- [ ] `/api/admin/**` restringido a `ADMIN` (otro rol → `403`)
- [ ] `GET /api/admin/users` con `role`, `page`, `size`, `sort`, `q` y respuesta `PagedModel`
- [ ] `GET /api/admin/users/stats`
- [ ] `GET /api/admin/users/{id}`
- [ ] `POST /api/admin/users` (correo duplicado → `422`)
- [ ] `PUT /api/admin/users/{id}` (conserva `created_at`, permite cambiar el rol)
- [ ] Ningún endpoint expone cuentas `ADMIN` ni contraseñas
- [ ] Pruebas y `docs/API.md` actualizados

**Fase 2**
- [ ] Migración con `app_users.active`
- [ ] `active` en respuestas, filtro y estadísticas
- [ ] `PATCH /api/admin/users/{id}/status`
- [ ] Cuentas inactivas no pueden iniciar sesión ni usar tokens ya emitidos
- [ ] `USER_STATUS_ENABLED = true` en el front

**Fase 3**
- [ ] Especialidad del proveedor
- [ ] Nombre del admin en el menú lateral
- [ ] Política de contraseña (si el PO la aprueba)

---

## 8. Archivos del frontend relacionados

| Archivo | Para qué sirve |
|---|---|
| `features/admin/data/admin-user.model.ts` | Tipos del contrato (`AdminUser`, `Page`, `UserStats`…) |
| `features/admin/data/admin-users.service.ts` | Llamadas HTTP a `/api/admin/users` |
| `features/admin/data/admin-mock.interceptor.ts` | Backend simulado (referencia del contrato) |
| `features/admin/admin.config.ts` | `USER_STATUS_ENABLED` y activación del simulador |
| `features/admin/admin.routes.ts` | Rutas del panel |
| `core/role.guard.ts` | `adminGuard` |

Todas las rutas son relativas a `frontend/src/app/`.
