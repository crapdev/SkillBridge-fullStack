# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
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

## Suggested next endpoints

```text
POST   /api/provider/offerings
PUT    /api/provider/offerings/{id}
PATCH  /api/provider/offerings/{id}/status
GET    /api/admin/metrics/business
```

The provider write endpoints should invalidate the public offerings cache through `OfferingCachePort`.

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
