# BUG-07 · Un horario con una reserva cancelada no se puede volver a reservar

Jira: [SCRUM-383](https://calborparra.atlassian.net/browse/SCRUM-383) · Relacionado: BUG-06 ([SCRUM-382](https://calborparra.atlassian.net/browse/SCRUM-382), [reporte](BUG-06-cancelar-no-libera-horario.md))

## 1. Reporte

**Pasos para reproducir**
1. El cliente A reserva un horario y lo cancela con más de 24 horas de anticipación.
2. (Antes de BUG-06) marcar a mano la franja como `reserved = false`, para aislar este bug.
3. El cliente B intenta reservar esa misma franja.
4. El cliente A intenta reservar otra mentoría a la misma hora que canceló.

**Esperado:** las dos reservas se crean.
**Observado:** B recibe *"Este horario ya fue reservado por otro usuario"* y A recibe *"Ya tienes otra mentoría agendada en este mismo horario"*.

## 2. Causa raíz

`BookingService.create` hace dos comprobaciones antes de crear la reserva:

1. `existsByOfferingIdAndScheduledAt`: ¿alguien más reservó esta mentoría a esta hora?
2. `existsByCustomerIdAndScheduledAt`: ¿este cliente ya tiene otra mentoría a esta hora?

Las dos eran **consultas derivadas de Spring Data sin filtro de estado**: `SELECT ... WHERE offering_id = ? AND scheduled_at = ?`. Contaban también las reservas `CANCELLED`, así que una reserva cancelada bloqueaba el horario para siempre.

La base de datos no tiene ninguna restricción `UNIQUE` sobre `bookings (offering_id, scheduled_at)`, así que el bloqueo venía solo de estas dos consultas.

**Efecto en los datos:** con datos reales hay menos reservas de las que debería, lo que distorsiona la ocupación (HU-45) y los clientes recurrentes (HU-50).

## 3. Solución

| Capa | Cambio |
|---|---|
| Puerto (`BookingRepositoryPort`) | Los métodos pasan a llamarse `existsActiveByOfferingIdAndScheduledAt` y `existsActiveByCustomerIdAndScheduledAt`, para que el nombre diga que solo cuentan reservas activas |
| Repositorio JPA | `existsByOfferingIdAndScheduledAtAndStatusNotIn` y `existsByCustomerIdAndScheduledAtAndStatusNotIn` reemplazan a las consultas sin filtro |
| Adaptador (`BookingPersistenceAdapter`) | Constante `RELEASED_STATUSES = {CANCELLED}`: los estados que ya no ocupan el horario. Si HU-37 agrega `EXPIRED`, solo hay que sumarlo ahí |
| Servicio (`BookingService.create`) | Usa los métodos nuevos. Los mensajes de error no cambian |

Una reserva `CREATED`, `CONFIRMED` o `COMPLETED` sigue bloqueando el horario. No hace falta migración Flyway.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

`BookingSlotAvailabilityPersistenceTest` (Testcontainers con PostgreSQL 17) se escribió primero contra las consultas originales. Las dos pruebas con una reserva `CANCELLED` fallaron porque la consulta la seguía contando. Las cuatro con reservas activas pasaron, como debe ser:

```text
[ERROR] cancelledBookingDoesNotBlockTheCustomerAtThatTime:82
  [el cliente que canceló puede reservar otra mentoría a esa hora]
  Expecting value to be false but was true
[ERROR] cancelledBookingDoesNotBlockTheSlotForOtherCustomers:73
  [una reserva cancelada no debe impedir que otro cliente reserve esa franja]
  Expecting value to be false but was true
[ERROR] Tests run: 6, Failures: 2, Errors: 0, Skipped: 0
```

### 4.2 Pruebas automatizadas que cubren la regresión

| Prueba | Qué demuestra |
|---|---|
| `BookingSlotAvailabilityPersistenceTest` (Testcontainers) | En PostgreSQL, una reserva `CANCELLED` no cuenta para la mentoría ni para el cliente; `CREATED`, `CONFIRMED` y `COMPLETED` sí; si a esa hora hay una cancelada y una confirmada, la confirmada bloquea |
| `BookingServiceTest.shouldAllowBookingASlotWhoseOnlyBookingWasCancelled` | Otro cliente reserva la franja liberada: se crea la reserva y la franja queda `reserved = true` |
| `BookingServiceTest.shouldRejectBookingWhenAnotherActiveBookingExistsAtThatTime` | Con una reserva activa de otro cliente se sigue rechazando con *"Este horario ya fue reservado por otro usuario"* |
| `BookingServiceTest.shouldRejectBookingWhenCustomerHasAnotherActiveBookingAtThatTime` | Con otra reserva activa del mismo cliente se sigue rechazando con *"Ya tienes otra mentoría agendada en este mismo horario"* |

Resultado de `mvn verify` en la rama, con Docker: **392 pruebas, 0 fallos**, 0 violaciones de Checkstyle y ArchUnit en verde.

## 5. Relación con BUG-06

Hacían falta los dos arreglos para que un horario cancelado se vuelva a vender:

| | Sin BUG-06 | Con BUG-06 |
|---|---|---|
| **Sin BUG-07** | La franja queda `reserved = true` y además la reserva cancelada bloquea | La franja vuelve a estar libre y aparece en el catálogo, pero al reservar sale *"Este horario ya fue reservado"* |
| **Con BUG-07** | La consulta ya no bloquea, pero la franja sigue `reserved = true` y no se muestra | ✅ La franja aparece y se puede reservar |

BUG-06 libera la franja (`availability_slots.reserved`) y BUG-07 hace que la reserva cancelada deje de contar (`bookings.status`).

## 6. Otros lugares revisados

- **`ProviderStatsService`:** el total de reservas recibidas incluye las canceladas, pero cada estado se cuenta por separado (`cancelled`, `upcoming`, etc.). Es intencional y no cambia.
- **`BookingRepositoryPort.existsDuplicateBooking`:** tampoco filtra el estado, pero no se usa en ningún lado. Si alguien lo vuelve a usar, debería excluir `RELEASED_STATUSES` igual que las otras dos.
- **HU-37:** si agrega un estado como `EXPIRED`, hay que sumarlo a `RELEASED_STATUSES` en `BookingPersistenceAdapter`.
