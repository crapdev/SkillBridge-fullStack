# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog (paged); each item includes the offering profile (HU-20) |
| GET | `/offerings/{id}` | Public | Offering detail with syllabus, duration, level and working days |
| POST | `/provider/offerings` | PROVIDER | Create offering with its full profile (HU-20) |
| PUT | `/provider/offerings/{id}` | PROVIDER | Edit own offering and its profile (HU-19 / HU-20) |
| POST | `/provider/offerings/{id}/slots` | PROVIDER | Publish a slot; only on the offering working days (HU-20) |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |

OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Booking Endpoints

### Cancelar Reserva

`PATCH /api/bookings/{id}/cancel`

Permite a un cliente autenticado cancelar una reserva propia.

**Requisitos:**
- **Autenticación:** Bearer JWT requerido.
- **Ruta:** `id` (UUID de la reserva a cancelar).

**Reglas:**
- Solo se pueden cancelar reservas en estado `CREATED` o `CONFIRMED`.
- Se requiere al menos 24 horas de antelación respecto a `scheduledAt`.
- Intentar acceder a una reserva inexistente o de otro usuario retornará un error genérico para no revelar información.

**Posibles Respuestas:**
- `200 OK`: La reserva ha sido cancelada exitosamente. Retorna el objeto `Booking` actualizado.
- `401 Unauthorized`: El usuario no está autenticado o el token es inválido.
- `403 Forbidden`: El usuario intenta cancelar una reserva que no es de su propiedad.
- `404 Not Found`: La reserva con el ID indicado no existe.
- `422 Unprocessable Entity`: Violación de regla de negocio (ej: ya cancelada, completada, o fuera del margen de 24 horas).

## Mentorías del proveedor (contrato HU-20)

Requieren Bearer JWT con rol `PROVIDER`. Detalle completo, reglas y ejemplos en
[`HU-20-ficha-completa-de-la-mentoria.md`](HU-20-ficha-completa-de-la-mentoria.md).

`POST /api/provider/offerings` y `PUT /api/provider/offerings/{id}` reciben el mismo cuerpo:

```json
{
  "title": "Kubernetes desde cero",
  "description": "Primeros pasos con pods y despliegues",
  "category": "CLOUD",
  "price": 90000,
  "syllabus": [
    { "title": "Pods", "description": "Qué son y cómo se crean" },
    { "title": "Deployments", "description": "Réplicas y actualizaciones" }
  ],
  "durationMinutes": 90,
  "level": "INTERMEDIO",
  "workingDays": ["MONDAY", "WEDNESDAY"]
}
```

| Campo | Regla |
|---|---|
| `syllabus` | 2 a 12 temas; cada uno con `title` (máx. 120) y `description` (máx. 300) |
| `durationMinutes` | 30 a 240 |
| `level` | `BASICO`, `INTERMEDIO` o `AVANZADO` |
| `workingDays` | Al menos un día: `MONDAY` … `SUNDAY` |

**Respuestas:** `201`/`200` con la mentoría completa · `400` si falla una regla (p. ej. `"syllabus: El temario debe tener al menos 2 temas"`) · `403` si la mentoría es de otro proveedor · `404` si no existe.

`POST /api/provider/offerings/{id}/slots` responde `422` si la fecha (en hora de Colombia) cae en un día que la mentoría no ofrece.

Crear, editar o eliminar una mentoría y publicar o borrar horarios invalidan la caché del catálogo público (`OfferingCachePort`).

## Suggested next endpoints

```text
PATCH  /api/provider/offerings/{id}/status
GET    /api/admin/metrics/business
```

## Admin Users Endpoints

Requieren autenticación mediante Bearer JWT con rol `ADMIN`. 
Los endpoints utilizan el formato `ProblemDetail` para el manejo de errores. 
Ningún endpoint lista, expone o permite modificar cuentas con rol `ADMIN`.

### Listar Usuarios
`GET /api/admin/users`

Lista cuentas de proveedores y clientes, con soporte de paginación y búsqueda. 

**Parámetros:**
- `role` (Obligatorio): `PROVIDER` o `CUSTOMER`. (400 si falta o es ADMIN).
- `page` (Opcional): Número de página (default 0).
- `size` (Opcional): Tamaño de página (default 10).
- `sort` (Opcional): Criterio de orden (default `createdAt,desc`).
- `q` (Opcional): Búsqueda parcial en `name` o `email` (ignorando mayúsculas).

**Respuestas:**
- `200 OK`: `PagedModel` con `content` (lista de `AdminUser`) y `page` (detalles de paginación).
- `400 Bad Request`: Si falta el parámetro `role` o es inválido/ADMIN.

### Indicadores de Usuarios
`GET /api/admin/users/stats`

Retorna estadísticas sobre proveedores y clientes.

**Respuestas:**
- `200 OK`: Objeto con `providers` y `customers`. Cada uno contiene `total` y `newThisMonth` (creados desde el 1er día del mes actual en America/Bogota).

### Detalle de Usuario
`GET /api/admin/users/{id}`

Retorna la información de un usuario específico.

**Respuestas:**
- `200 OK`: Objeto `AdminUser`.
- `404 Not Found`: Si el usuario no existe o tiene rol `ADMIN`.

### Crear Usuario
`POST /api/admin/users`

Crea una cuenta nueva para proveedor o cliente.

**Body:**
```json
{
  "name": "Ana Pérez",
  "email": "ana.perez@cloudexpert.co",
  "password": "password123",
  "role": "PROVIDER"
}
```

**Respuestas:**
- `201 Created`: Usuario creado exitosamente (retorna objeto `AdminUser`).
- `400 Bad Request`: Errores de validación de campos, o si `role` es `ADMIN`.
- `422 Unprocessable Entity`: Si el correo ya está registrado (`detail: "El correo ya está registrado"`).

### Editar Usuario
`PUT /api/admin/users/{id}`

Edita la información de una cuenta existente. No modifica la contraseña ni la fecha de creación.

**Body:**
```json
{
  "name": "Ana Pérez Modificada",
  "email": "ana.perez2@cloudexpert.co",
  "role": "CUSTOMER"
}
```

**Respuestas:**
- `200 OK`: Usuario actualizado (retorna objeto `AdminUser`).
- `400 Bad Request`: Errores de validación o intento de cambiar rol a `ADMIN`.
- `404 Not Found`: Si el usuario no existe o es `ADMIN`.
- `422 Unprocessable Entity`: Si el nuevo correo ya pertenece a otra cuenta.
