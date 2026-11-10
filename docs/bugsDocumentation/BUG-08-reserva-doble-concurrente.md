# BUG-08 · Dos clientes pueden reservar el mismo horario al mismo tiempo

Jira: [SCRUM-384](https://calborparra.atlassian.net/browse/SCRUM-384) · Relacionado: BUG-07 ([reporte](BUG-07-horario-cancelado-bloqueado.md)), HU-37

> **Pregunta de la sustentación:** *¿Cómo evitan que dos estudiantes reserven la misma franja a la vez?*
>
> **Respuesta corta:** con un **índice único parcial en PostgreSQL** sobre `bookings (offering_id, scheduled_at)` que solo cuenta las reservas activas. Aunque dos peticiones pasen al mismo tiempo la revisión del código, la base de datos solo deja guardar una. La otra recibe **409 Conflict** (*"Este horario acaba de ser apartado"*), y como la reserva se crea en una **transacción**, su franja no queda marcada como reservada.

## 1. Reporte

**Pasos para reproducir**
1. Tener una franja libre de una mentoría aprobada.
2. Enviar dos (o más) `POST /api/bookings` para esa franja al mismo tiempo, con clientes distintos.
3. Revisar la tabla `bookings`.

**Esperado:** se crea una reserva y las demás peticiones reciben 409.
**Observado:** quedan varias reservas activas para la misma mentoría y la misma hora.

## 2. Causa raíz

`BookingService.create` hacía **revisar y después insertar** (*check-then-act*) sin nada que lo protegiera:

```text
Cliente A                                  Cliente B
---------                                  ---------
¿la franja está libre?  → sí
                                           ¿la franja está libre?  → sí (A todavía no guarda)
marca la franja reserved = true
guarda la reserva de A
                                           marca la franja reserved = true
                                           guarda la reserva de B   ← reserva doble
```

1. **Sin transacción:** cada `save` se confirmaba por separado. Si la reserva fallaba, la franja ya había quedado `reserved = true` sin una reserva detrás.
2. **Sin bloqueo:** ni `@Version` ni bloqueo pesimista, así que las dos peticiones leían la franja libre.
3. **Sin restricción en la base:** `bookings` no tenía ningún índice único, así que PostgreSQL aceptaba las dos filas.

## 3. Solución

| Capa | Cambio |
|---|---|
| Base de datos (`V10__bookings_active_slot_unique.sql`) | `CREATE UNIQUE INDEX uq_bookings_active_slot ON bookings (offering_id, scheduled_at) WHERE status <> 'CANCELLED'`. Es parcial: una reserva cancelada no bloquea, igual que en BUG-07 |
| Servicio (`BookingService.create`) | `@Transactional`: la franja reservada y la reserva se guardan juntas o no se guarda ninguna |
| Persistencia (`BookingPersistenceAdapter.save`) | `saveAndFlush` para que el índice falle en ese momento y no al confirmar la transacción. Si falla por `uq_bookings_active_slot`, lanza `SlotAlreadyTakenException`. Cualquier otro error de integridad se deja pasar igual |
| Dominio | `SlotAlreadyTakenException`: "Este horario acaba de ser apartado por otro usuario. Elige otro horario." |
| API (`GlobalExceptionHandler`) | `SlotAlreadyTakenException` → **409 Conflict**. El caso normal de "ya estaba reservado" sigue respondiendo 422 |
| Frontend (`booking.component.ts`) | Con un 409, el formulario muestra el mensaje y **quita ese horario de la lista**, igual que cuando la reserva sale bien. Así quien perdió la carrera no lo sigue viendo como libre. Los demás errores (422) no quitan el horario |

### ¿Por qué la garantía está en la base de datos y no solo en el código?

- **El código no ve lo que no se ha confirmado.** Las dos peticiones corren en hilos (o en servidores) distintos. Las dos leen la franja antes de que la otra guarde, así que cualquier `if` en Java pasa en las dos.
- **Un `synchronized` o un bloqueo en memoria no sirve** en cuanto hay más de una instancia del backend (Render puede levantar otra), y además no protege contra otro proceso que escriba en la base.
- **El índice único es atómico.** PostgreSQL revisa la restricción dentro de la misma operación de escritura. Si dos transacciones insertan la misma clave, la segunda espera a la primera y, cuando la primera confirma, falla con `duplicate key`. No hay ventana entre "revisar" y "guardar".
- **Las revisiones del código se quedan** porque dan el mensaje normal ("Este horario ya fue reservado") en el caso común, sin llegar a la base. El índice es la red de seguridad para el caso concurrente.

### Datos que ya existían

El índice no se puede crear si la base ya tiene duplicados. Por eso V10 primero deja **una reserva activa por franja** y pasa las demás a `CANCELLED`. Se queda con `COMPLETED`, después con `CONFIRMED` (ya pagada) y, si empatan, con la que se creó primero.

- En la base local de desarrollo había 15 reservas y **ningún duplicado**.
- **Antes de desplegar en producción**, corre esta consulta. Si devuelve filas con dos reservas pagadas, hay que devolverle el dinero a mano al cliente cuya reserva se cancele:

  ```sql
  SELECT offering_id, scheduled_at, COUNT(*), string_agg(status, ',')
  FROM bookings WHERE status <> 'CANCELLED'
  GROUP BY offering_id, scheduled_at HAVING COUNT(*) > 1;
  ```

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

`BookingConcurrencyIntegrationTest` usa el `BookingService` real contra PostgreSQL 17 (Testcontainers). Lanza **5 clientes a la vez** sobre la misma franja (`CountDownLatch` para arrancarlos juntos). Una `CyclicBarrier` detiene a los 5 justo después de que comprobaron que la franja estaba libre y antes de guardar. Así el peor caso de la carrera ocurre **en cada corrida** y no depende de la suerte. Antes del arreglo:

```text
[ERROR] onlyOneOfSeveralSimultaneousBookingsWinsTheSlot:167 [solo un cliente debe obtener la franja]
  expected: 1L
   but was: 5L
[ERROR] slotIsNotLeftReservedWhenTheBookingCannotBeSaved:190 [la segunda reserva para la misma franja debe rechazarse]
  Expecting actual not to be null
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
```

**Ganaron los 5 clientes.**

### 4.2 Pruebas automatizadas que cubren la regresión

| Prueba | Qué demuestra |
|---|---|
| `BookingConcurrencyIntegrationTest.onlyOneOfSeveralSimultaneousBookingsWinsTheSlot` | De 5 reservas simultáneas gana exactamente 1; las otras 4 reciben `SlotAlreadyTakenException`; en la base hay 1 reserva activa y la franja queda reservada |
| `BookingConcurrencyIntegrationTest.slotIsNotLeftReservedWhenTheBookingCannotBeSaved` | Si otra reserva ganó entre la revisión y el guardado, esta se rechaza y **la franja no queda reservada** (la transacción se deshace) |
| `BookingControllerTest` | `SlotAlreadyTakenException` responde **409** con el mensaje; "ya estaba reservado" sigue en 422 |
| `booking.component.spec.ts` (frontend) | Con un 409, el horario desaparece de la lista, se deselecciona y se muestra el mensaje; con un 422 el horario se queda; una reserva exitosa lo quita como antes |

- La prueba de concurrencia pasó en **4 de 4 corridas** seguidas.
- La limpieza de duplicados de V10 se probó en un PostgreSQL desechable con franjas duplicadas. Dejó la reserva correcta en cada una, no tocó las canceladas y después el índice rechazó un duplicado nuevo.
- `mvn verify`: **396 pruebas, 0 fallos**, 0 violaciones de Checkstyle y ArchUnit en verde.
- Frontend (`ng test`): las 3 pruebas nuevas pasan (128 en verde). La única que falla es `AiComponent` (BUG-02), que ya fallaba igual en `develop` antes de este cambio.

### 4.3 Probarlo a mano (opcional)

Con la app levantada (`docker compose up --build -d`), dos clientes con sesión iniciada (`TOKEN_A`, `TOKEN_B`) y una franja libre:

```bash
BODY='{"offeringId":"<id-mentoria>","scheduledAt":"<fecha-de-la-franja>"}'
curl -s -o /dev/null -w "A: %{http_code}\n" -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d "$BODY" &
curl -s -o /dev/null -w "B: %{http_code}\n" -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d "$BODY" &
wait
```

Una petición responde `200` y la otra `409`. Si la segunda llega cuando la primera ya terminó, responde `422` ("ya fue reservado"), que también es correcto.

## 5. Lo que queda fuera

- **Un mismo cliente reservando dos mentorías distintas a la misma hora al mismo tiempo:** el índice es por mentoría, así que ese caso solo lo frena la revisión del código. Es mucho menos probable (son dos clics del mismo usuario) y se puede cubrir después con otro índice parcial sobre `(customer_id, scheduled_at)`.
- **Evento `booking.created` dentro de la transacción:** se publica después de guardar con *flush*, así que una reserva rechazada no genera evento. Si en el futuro algo pudiera fallar después del evento, convendría publicarlo al confirmar la transacción.
- **HU-37:** si agrega estados como `EXPIRED`, hay que sumarlos al `WHERE` del índice (nueva migración) y a `RELEASED_STATUSES` en `BookingPersistenceAdapter`.
