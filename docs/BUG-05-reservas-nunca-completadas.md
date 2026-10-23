# BUG-05 · Las reservas nunca pasan a COMPLETED



## 1. Reporte

**Pasos para reproducir**
1. Reservar una mentoría y pagarla con la tarjeta de prueba de Stripe `4242 4242 4242 4242`: la reserva pasa a `CONFIRMED`.
2. Esperar a que pase la hora de la sesión (o mover `scheduled_at` al pasado en la base local).
3. Revisar el estado de la reserva (`bookings.status`, *Mis reservas* y el panel del proveedor).

**Esperado:** después de la hora de fin (`scheduled_at + duration_minutes`) la reserva queda `COMPLETED`.
**Observado:** la reserva se queda `CONFIRMED` para siempre. En la base no existe ninguna reserva `COMPLETED`.

## 2. Causa raíz

El enum `BookingStatus` tiene `COMPLETED`, pero **nada hacía la transición**:

1. `Booking` solo tenía `cancel()` y `confirm()`; no existía una regla de dominio para completar.
2. Ningún servicio, proceso programado ni endpoint cambiaba una reserva a `COMPLETED` (la aplicación ni siquiera tenía `@EnableScheduling`).
3. `ProviderStatsService` contaba las completadas, pero el resultado siempre era 0.

## 3. Solución

| Capa | Cambio |
|---|---|
| Dominio | `Booking.endsAt(duración)`, `isFinished(now, duración)` y `complete(now, duración)`: solo una reserva `CONFIRMED` cuya sesión terminó puede completarse; en otro caso, `BusinessRuleException` |
| Aplicación | `CompleteFinishedBookingsUseCase` / `BookingCompletionService` con el `Clock` de BUG-04. La duración sale de la ficha de la mentoría (60 min si la mentoría ya no existe) |
| Proceso programado | `BookingCompletionScheduler` cada 5 minutos. Configurable con `BOOKING_COMPLETION_INTERVAL_MS` y `BOOKING_COMPLETION_ENABLED` |
| Al consultar | *Mis reservas* (`BookingService.listMyBookings`) y el panel del proveedor (`ProviderStatsService`) completan las sesiones terminadas antes de responder, porque Render gratis se duerme y el proceso puede no haber corrido |
| Persistencia | `updateStatusIf(id, CONFIRMED, COMPLETED)`: un `UPDATE ... WHERE status = 'CONFIRMED'` atómico. Solo quien hace el cambio publica el evento |
| Mensajería | Evento `booking.completed` en el exchange `booking.events` (notificaciones y n8n) |
| Notificaciones | Tipo `BOOKING_COMPLETED`: el proveedor recibe "Se completó la sesión de … del …" y la campana del frontend lo muestra como "Sesión completada" |

No hace falta migración Flyway: `status` ya admitía `COMPLETED` y `notifications.type` es texto.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

Las pruebas se escribieron primero (commit `a2f5586`, SCRUM-388) y fallaron: no compilaban porque no existían ni la transición ni el proceso de completado, que es justamente la causa raíz.

```text
[ERROR] application/service/BookingCompletionServiceTest.java:[36,19] cannot find symbol
  symbol:   class BookingCompletionService
[INFO] BUILD FAILURE
```

### 4.2 Pruebas automatizadas que cubren la regresión

| Prueba | Qué demuestra |
|---|---|
| `BookingCompletionTest` | Con un `Clock` fijo después de la hora de fin, la reserva queda `COMPLETED`; justo al terminar también; durante la sesión no; `CREATED`, `CANCELLED` y `COMPLETED` no se pueden completar |
| `BookingCompletionServiceTest` | Una reserva `CONFIRMED` en el pasado queda `COMPLETED` y se publica el evento; una sesión en curso, las `CREATED`, `CANCELLED` y futuras no cambian; si otro proceso ya la completó, el evento no se repite; la duración de cada mentoría se busca una sola vez |
| `BookingCompletionPersistenceTest` (Testcontainers) | En PostgreSQL, solo se encuentran las confirmadas que ya empezaron y el cambio a `COMPLETED` ocurre una sola vez |
| `ProviderStatsServiceTest`, `BookingServiceTest` | Siguen pasando con el completado al consultar |

Resultado de la suite completa en la rama: **376 pruebas, 0 fallos** (`mvn verify`, con Checkstyle y ArchUnit).

## 5. Para las siguientes historias

- **HU-27 / HU-47:** una reseña solo se permite si `booking.status() == COMPLETED`. Para probarlo, crear la reserva `CONFIRMED` y llamar a `complete(now, duración)` con un `Clock` fijo.
- **HU-37:** si agrega estados nuevos a la reserva, revisar que `complete()` siga aceptando solo `CONFIRMED`.
- **n8n:** el flujo debe escuchar la routing key `booking.completed`. El evento solo lleva ids (`bookingId`, `offeringId`, `customerId`, `scheduledAt`, `occurredAt`).
