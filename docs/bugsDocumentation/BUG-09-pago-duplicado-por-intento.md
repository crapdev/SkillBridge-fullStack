# BUG-09 · Cada intento de pago crea un registro de pago nuevo

Jira: [SCRUM-385](https://calborparra.atlassian.net/browse/SCRUM-385) · Relacionado: BUG-08 ([reporte](BUG-08-reserva-doble-concurrente.md)), HU-37, HU-48, HU-50

> **Respuesta corta:** ahora cada reserva tiene **un solo pago activo** (`PENDING` o `PAID`). Si el cliente vuelve a abrir la pantalla de pago, el backend **reutiliza el mismo PaymentIntent de Stripe** en vez de crear otro. La regla la garantiza un **índice único parcial en PostgreSQL** (`uq_payments_active_booking`). Los pagos `FAILED` no cuentan, así que después de un rechazo el cliente puede volver a intentar.

## 1. Reporte

**Pasos para reproducir**
1. Reservar una mentoría y abrir la pantalla de pago.
2. Salir sin pagar y volver a abrir el pago de la misma reserva varias veces.
3. Revisar la tabla `payments` filtrando por ese `booking_id`.

**Esperado:** un solo pago `PENDING` por reserva, que se reutiliza mientras siga pendiente.
**Observado:** un pago `PENDING` nuevo (con un PaymentIntent de Stripe nuevo) cada vez que se abre el pago. Los anteriores quedan abandonados.

**Impacto en datos:** la conciliación de HU-50 no cuadra (hay muchos más pagos que reservas) y HU-48 sumaría dos veces si una reserva quedara con dos `PAID`.

## 2. Causa raíz

`CreatePaymentService.createPaymentIntent` hacía siempre lo mismo:

```text
POST /api/payments/intent  →  Stripe: crear PaymentIntent  →  INSERT payments (PENDING)
```

1. **No buscaba un pago pendiente** de la reserva antes de crear otro.
2. **La tabla no lo impedía:** en `payments` solo `stripe_payment_intent_id` era único. Nada limitaba los pagos por `booking_id`.
3. Cada vez que se monta `payment.component.ts` (abrir la pantalla, recargar, volver desde *Mis reservas*) se llama a `/payments/intent`, así que cada visita dejaba una fila nueva.

## 3. Solución

| Capa | Cambio |
|---|---|
| Base de datos (`V11__payments_one_active_per_booking.sql`) | `CREATE UNIQUE INDEX uq_payments_active_booking ON payments (booking_id) WHERE status IN ('PENDING', 'PAID')`. Antes de crearlo, limpia los duplicados que ya existían (ver 3.2) |
| Servicio (`CreatePaymentService`) | Antes de crear un cobro busca el pago activo de la reserva y decide qué hacer con él (ver 3.1) |
| Pasarela (`PaymentGatewayPort` / `StripePaymentGatewayAdapter`) | Dos operaciones nuevas: `retrievePaymentIntent` (estado del cobro y su secreto para reutilizarlo) y `cancelPaymentIntent` (cancela un cobro que ya no se va a usar; devuelve `false` si ya se pagó) |
| Persistencia (`PaymentPersistenceAdapter`) | `findActiveByBookingId`. `save` usa `saveAndFlush` y, si falla por `uq_payments_active_booking`, lanza `ActivePaymentAlreadyExistsException` |
| Webhook (`ProcessPaymentService.confirmPaymentSuccess`) | Si llega el pago de un intento viejo mientras hay otro pendiente, cancela el pendiente y registra el pago real (ver 3.3) |

### 3.1 Qué pasa al abrir el pago

| Pago activo de la reserva | Estado en Stripe | Resultado |
|---|---|---|
| Ninguno (o solo `FAILED`) | — | Se crea un PaymentIntent y un pago `PENDING` (el reintento después de un rechazo sigue funcionando) |
| `PENDING` | Esperando el pago | **Se reutiliza:** se devuelve el mismo `clientSecret`. No se crea nada |
| `PENDING` | Esperando el pago, pero **el precio cambió** | Se cancela en Stripe, el viejo pasa a `FAILED` y se crea uno con el precio actual |
| `PENDING` | Cancelado (o Stripe ya no lo conoce) | El viejo pasa a `FAILED` y se crea uno nuevo |
| `PENDING` | Pagado o procesando (el webhook aún no llega) | **422** *"Ya recibimos un pago para esta reserva"*. No se cobra dos veces |
| `PAID` | — | **422** *"Esta reserva ya fue pagada"* |

**Dos peticiones al mismo tiempo** (doble clic, dos pestañas): las dos ven que no hay pago activo y crean un cobro en Stripe, pero el índice solo deja guardar uno. La que pierde **cancela su cobro en Stripe** y devuelve el secreto del que ganó, así que las dos pestañas quedan pagando el mismo PaymentIntent.

### 3.2 Datos que ya existían

El índice no se puede crear si ya hay duplicados. V11:

1. **Se detiene** si alguna reserva tiene dos pagos `PAID`. Eso es un cobro doble: hay que revisarlo y reembolsar a mano. La migración no decide cuál de los dos vale.
2. Deja **un pago activo por reserva**: el `PAID` si existe y, si no, el `PENDING` más reciente (el último que abrió el cliente). Los demás `PENDING` pasan a `FAILED`.

- En la base local de desarrollo no había pagos, así que no había nada que limpiar.
- **Antes de desplegar en producción**, revisa los duplicados con:

  ```sql
  SELECT booking_id, COUNT(*), string_agg(status, ',')
  FROM payments WHERE status IN ('PENDING', 'PAID')
  GROUP BY booking_id HAVING COUNT(*) > 1;
  ```

- Los PaymentIntent de los pagos que V11 pasa a `FAILED` siguen abiertos en Stripe. Nadie los puede pagar sin su `clientSecret`, pero si se quieren cerrar se pueden cancelar desde el panel de Stripe.

### 3.3 El webhook con el índice nuevo

Un cliente puede tener el formulario de un intento viejo abierto en otra pestaña y pagarlo mientras hay otro intento `PENDING`. Sin cuidado, marcar el viejo como `PAID` violaría el índice. El webhook respondería 500 y Stripe lo reintentaría durante días. Por eso `confirmPaymentSuccess`:

- Si el otro pago activo es `PENDING` y se puede cancelar en Stripe: lo cancela, lo pasa a `FAILED` y registra el pago real como `PAID`.
- Si el otro ya está `PAID` (o ya se cobró en Stripe): es un **cobro doble**. No se guarda un segundo `PAID`, se escribe un `log.error` con los dos PaymentIntent para reembolsar a mano y se responde 200 a Stripe.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

`CreatePaymentSinglePaymentTest` usa el `CreatePaymentService` real con una **pasarela simulada** y un repositorio de pagos en memoria. Llama `createPaymentIntent` dos veces para la misma reserva y cuenta los pagos guardados para ese `booking_id`. Antes del arreglo:

```text
[ERROR] CreatePaymentSinglePaymentTest.openingThePaymentTwice_keepsASinglePaymentForTheBooking:69
Expected size: 1 but was: 2 in:
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

El caso adicional (*después de un pago FAILED, volver a pagar funciona*) ya pasaba antes y sigue pasando después del arreglo.

### 4.2 Pruebas automatizadas que cubren la regresión

| Prueba | Qué demuestra |
|---|---|
| `CreatePaymentSinglePaymentTest` (7) | Abrir el pago dos veces deja 1 registro y el mismo secreto; reintentar tras `FAILED` crea uno nuevo; si cambió el precio se cancela el viejo; si Stripe lo canceló se crea otro; si Stripe ya cobró o la reserva está `PAID` no se cobra otra vez; en una carrera, el perdedor cancela su cobro y usa el del ganador |
| `PaymentOneActivePerBookingIntegrationTest` (5, PostgreSQL real) | Abrir el pago 3 veces deja 1 fila `PENDING`; la base rechaza un segundo `PENDING` y un segundo `PAID`; los `FAILED` no bloquean el reintento; **5 peticiones simultáneas** (con `CyclicBarrier` para forzar la carrera) dejan 1 pago, las 5 reciben el mismo secreto y se cancelan los otros 4 cobros |
| `PaymentsMigrationV11Test` (2) | V11 deja el `PAID` o el `PENDING` más reciente y pasa el resto a `FAILED`; con un cobro doble (`PAID` + `PAID`) se detiene sin tocar los datos |
| `ProcessPaymentServiceTest` (+3) | El webhook de un intento viejo cancela el pendiente y registra el pago; un segundo cobro de una reserva ya pagada no se guarda como otro `PAID` |
| `StripePaymentGatewayAdapterTest` (+1) | Traducción de los estados de Stripe a `IntentState` |
| `BookingWithSeveralPaymentsTest` (ajustada) | Una reserva con un intento `FAILED` y un reintento `PENDING` se sigue listando en *Mis reservas* (antes sembraba dos `PENDING`, que ahora la base no permite) |

- La prueba de 5 pagos simultáneos pasó en **4 de 4 corridas** seguidas.
- `mvn verify`: **414 pruebas, 0 fallos**, 0 violaciones de Checkstyle y las reglas de ArchUnit (`HexagonalArchitectureTest`) en verde.
- No hay cambios en el frontend: `payment.component.ts` sigue llamando a `/payments/intent` al abrir la pantalla y ahora recibe el mismo `clientSecret`.

### 4.3 Probarlo a mano (opcional)

Con la app levantada (`docker compose up --build -d`), un cliente con sesión iniciada (`TOKEN`) y una reserva `CREATED` (`BOOKING_ID`):

```bash
for i in 1 2 3; do
  curl -s -X POST localhost:8080/api/payments/intent -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" -d "{\"bookingId\":\"$BOOKING_ID\"}"; echo
done
```

Las tres respuestas traen el mismo `clientSecret`, y `SELECT status, COUNT(*) FROM payments WHERE booking_id = '<BOOKING_ID>' GROUP BY status;` muestra un solo `PENDING`.

## 5. Lo que queda fuera

- **BUG-10 (precio en la reserva):** hoy el monto sale de `offerings.price`. Cuando la reserva guarde su precio, `CreatePaymentService` debe comparar contra `bookings.price` (la lógica de "el precio cambió" de 3.1 sigue sirviendo).
- **HU-37 (expiración de la reserva):** al expirar debe cancelar el PaymentIntent con `cancelPaymentIntent` y pasar el pago a `FAILED`, o el índice seguirá contando ese `PENDING` como activo.
- **Contrato de datos:** `PAYMENT_STATUSES` en `data/src/skillbridge_data/schemas.py` no cambia (no hay estados nuevos). La regla de calidad para HU-50 puede asumir *como máximo un pago `PENDING`/`PAID` por reserva*.
