# BUG-10 · La reserva no guarda el precio que se cobró

Jira: [SCRUM-386](https://calborparra.atlassian.net/browse/SCRUM-386) · Relacionado: HU-19 y HU-20 (edición de la mentoría), HU-48, BUG-09 ([reporte](BUG-09-pago-duplicado-por-intento.md))

> **Respuesta corta:** ahora cada reserva guarda **el precio de la mentoría en el momento de reservar** (`bookings.price`). El pago cobra ese precio y no el precio actual de la mentoría, así que si el proveedor lo edita después, las reservas y los pagos que ya existían no cambian.

## 1. Reporte

**Pasos para reproducir**
1. Reservar una mentoría de 100.000.
2. Como proveedor, editar la mentoría y cambiar el precio a 120.000.
3. Abrir el pago de la reserva o comparar `payments.amount` con `offerings.price`.

**Esperado:** se cobra 100.000 y se puede saber que la reserva se hizo a ese precio.
**Observado:** el pago cobra **120.000**. La reserva no guarda ningún precio: el único rastro del precio original es `payments.amount`, y solo si ya se había pagado.

## 2. Causa raíz

1. `Booking` no tenía campo de precio (`bookings` tampoco tenía la columna).
2. `CreatePaymentService` calculaba el monto con `offering.price()`, es decir, con el precio **actual** de la mentoría.
3. `ProviderOfferingService.updateOffering` (HU-19) permite cambiar `price` en cualquier momento.

Juntos: cualquier cambio de precio afectaba a las reservas que todavía no se habían pagado. La pantalla de pago también mostraba `offering.price`.

**Efecto en los datos:** la regla de HU-48 *"el pago coincide con el precio"* compararía `payments.amount` contra `offerings.price` y marcaría como error todos los pagos hechos antes de un cambio de precio. Los reportes de ingresos por reserva tampoco tenían de dónde sacar el precio de las reservas no pagadas.

## 3. Solución

| Capa | Cambio |
|---|---|
| Base de datos (`V11__bookings_price.sql`) | `bookings.price NUMERIC(12,2) NOT NULL` con `CHECK (price >= 0)`. Llena las reservas existentes (ver 3.1) |
| Dominio (`Booking`) | Nuevo campo `price`. El record no admite un precio nulo o negativo, y `cancel()`, `confirm()` y `complete()` lo conservan |
| Persistencia (`BookingEntity` / `BookingPersistenceAdapter`) | Columna `price` con `updatable = false`: guardar una cancelación o una confirmación **no puede** cambiar el precio. El mapeo entidad → dominio quedó en un solo `toDomain` |
| Reserva (`BookingService.create`) | Copia `offering.price()` en la reserva |
| Pago (`CreatePaymentService`) | Cobra `booking.price()`. Ya no consulta la mentoría, así que una reserva pendiente se puede pagar aunque el proveedor haya borrado la mentoría |
| Frontend (`payment.component.ts`) | La pantalla de pago muestra `booking.price`: el monto que ve el cliente es el que se cobra |
| Contrato de datos | `bookings.price` en `schemas.py`, el fixture `bookings.csv` y `DATA-PIPELINE.md` |

La lógica de BUG-09 que reemplaza un cobro abierto por otro monto se mantiene. Ahora solo aplica a cobros abiertos **antes** de este arreglo, cuando se cobraba el precio de la mentoría.

### 3.1 Reservas que ya existían

V11 llena `price` así:

1. Si la reserva tiene un pago `PAID`, **ese monto**: es lo que de verdad se cobró, aunque el proveedor haya cambiado el precio después. BUG-09 garantiza que hay como máximo uno.
2. Si no, el precio actual de la mentoría. Un pago `FAILED` o `CANCELED` no cuenta: no es lo que se cobró.

Las reservas no pagadas toman el precio actual porque no hay otro dato. Es lo mismo que se habría cobrado sin este arreglo.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

`BookingPriceTest` recorre el flujo real: `BookingService` crea la reserva a 100.000, el proveedor sube el precio a 120.000 y `CreatePaymentService` prepara el cobro. Antes del arreglo:

```text
[ERROR] BookingPriceTest.changingTheOfferingPriceAfterBooking_chargesThePriceTheCustomerBookedAt
Argument(s) are different! Wanted:
paymentGatewayPort.createPaymentIntent(<reserva>, 100000, ...)
Actual invocations have different arguments at position [1]:
paymentGatewayPort.createPaymentIntent(<reserva>, 120000, ...)
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

**Se cobraba el precio nuevo.**

### 4.2 Pruebas automatizadas que cubren la regresión

| Prueba | Qué demuestra |
|---|---|
| `BookingPriceTest` (3) | Reservar a 100.000, subir a 120.000 y pagar cobra 100.000; la reserva guardada conserva 100.000; cancelar o confirmar no cambia el precio |
| `BookingsPriceMigrationV11Test` (2, PostgreSQL real) | Una reserva pagada a 100.000 conserva ese precio aunque la mentoría hoy valga 120.000; una con solo un pago `FAILED` o sin pagos toma el precio actual; después de V11, una reserva sin precio se rechaza |
| `CreatePaymentServiceTest` | Se cobra el precio de la reserva |
| `CreatePaymentSinglePaymentTest` | Un cobro abierto antes de BUG-10 por otro monto se cancela (`CANCELED`) y se cobra el precio de la reserva |
| `payment.component.spec.ts` (frontend) | Con la mentoría a 120.000 y la reserva a 100.000, el botón dice *Pagar $ 100.000* |

- `mvn verify`: **421 pruebas, 0 fallos**, 0 violaciones de Checkstyle y ArchUnit en verde.
- Frontend: `ng test` con 132 pruebas en verde. La única que falla es `AiComponent` (BUG-02), que ya fallaba en `develop`.
- Pipeline de datos: `pytest` en su contenedor con el contrato y el fixture nuevos.

## 5. Para las siguientes historias

- **HU-48:** la regla *"el pago coincide con el precio"* compara `payments.amount` con **`bookings.price`**, no con `offerings.price`.
- **HU-44 / HU-50 (ingresos):** el precio de una reserva es `bookings.price`, incluso para las que todavía no se pagaron.
- **HU-19 / HU-20:** editar el precio de una mentoría solo afecta a las reservas **nuevas**. Si alguna vez se quiere avisar al proveedor, esa es la regla que hay que explicarle.
- **Antes de desplegar:** V11 no se detiene en ningún caso, pero si quieres ver cuántas reservas tomarán el precio actual de la mentoría por no estar pagadas:

  ```sql
  SELECT COUNT(id) FROM bookings b
  WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.id AND p.status = 'PAID');
  ```
