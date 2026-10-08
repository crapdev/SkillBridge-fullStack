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
