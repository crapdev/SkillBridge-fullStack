# BUG-11 · Mentorías de ejemplo sin proveedor y estados sin restricción en la base

Jira: [SCRUM-387](https://calborparra.atlassian.net/browse/SCRUM-387) · Relacionado: HU-37 (estados nuevos), HU-44, HU-46, HU-47, HU-49

> **Respuesta corta:** desde V12 la base de datos ya no acepta datos imposibles. Las 3 mentorías de ejemplo de V1 pertenecen al proveedor de ejemplo `proveedor.demo@skillbridge.dev`, `offerings.provider_id` es obligatorio y `bookings.status` / `payments.status` solo admiten los estados que existen en el dominio.

## 1. Reporte

**Pasos para reproducir**
1. Levantar la base desde cero (`make reset`).
2. `SELECT id, title, provider_id FROM offerings WHERE provider_id IS NULL;`
3. `UPDATE bookings SET status = 'PAGADA' WHERE id = '<una reserva>';`

**Esperado:** toda mentoría tiene proveedor y la base rechaza un estado que no existe.
**Observado:** las 3 mentorías de ejemplo quedan con `provider_id` vacío, y la base acepta cualquier texto en `bookings.status` y `payments.status`.

## 2. Causa raíz

1. **V1** sembró 3 mentorías de ejemplo. **V2** agregó `offerings.provider_id` como columna **anulable** y no les asignó proveedor.
2. `bookings.status` y `payments.status` son `VARCHAR` sin `CHECK`. En cambio, `offerings.level` y `offerings.review_status` sí lo tienen desde V8 y V9.

**¿Afectaba a la aplicación?** No a los usuarios: el catálogo exige un proveedor con cuenta activa, así que esas mentorías no se veían, y nadie podía publicarles horarios. El problema está **en los datos**: tenían reservas de antes de que existieran los horarios (9 en la base local de desarrollo). En las métricas por proveedor (HU-44, HU-46, HU-47 y HU-49) esas reservas caían en un grupo **«sin proveedor»**. Y un error en el código o un `UPDATE` a mano podía dejar un estado inventado que ningún reporte sabría interpretar.

## 3. Solución: `V12__data_constraints.sql`

| Parte | Cambio |
|---|---|
| Proveedor de las mentorías de ejemplo | Se asignan a `proveedor.demo@skillbridge.dev`, el proveedor de ejemplo que ya crea V4. Si alguien lo borró, se vuelve a crear **sin acceso**: su contraseña no es un hash BCrypt, así que nadie puede iniciar sesión con esa cuenta |
| Salvaguarda | Si ese correo existe pero no es `PROVIDER`, la migración se detiene con un mensaje claro en lugar de asignarle mentorías a otra cuenta |
| `offerings.provider_id` | Pasa a `NOT NULL`. Desde HU-19 toda mentoría la crea un proveedor (`ProviderOfferingService`), así que no rompe nada |
| `chk_bookings_status` | `CREATED`, `CONFIRMED`, `CANCELLED`, `COMPLETED` (`BookingStatus`) |
| `chk_payments_status` | `PENDING`, `PAID`, `FAILED`, `CANCELED` (`PaymentStatus`, incluido `CANCELED` de BUG-09) |
| Contrato de datos | `provider_id` obligatorio en `schemas.py` y nota en `DATA-PIPELINE.md` |

**Qué cambia para el proveedor demo:** las 3 mentorías aparecen en su panel (*Mis mentorías*) y sus reservas viejas se le atribuyen en las métricas. Siguen sin verse en el catálogo mientras no tengan horarios publicados; si se inicia sesión con esa cuenta y se publican horarios, se vuelven reservables como cualquier otra.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

`DataConstraintsMigrationTest` parte de una base vacía y aplica todas las migraciones (PostgreSQL 17 con Testcontainers). Antes de V12:

```text
[ERROR] sampleOfferingsBelongToTheDemoProvider:83 [mentorías sin proveedor]
  expected: 0
   but was: 3
[ERROR] anInventedBookingStatusIsRejected       Expecting code to raise a throwable.
[ERROR] anInventedPaymentStatusIsRejected       Expecting code to raise a throwable.
[ERROR] anOfferingWithoutProviderIsRejected     Expecting code to raise a throwable.
[ERROR] Tests run: 5, Failures: 4, Errors: 0, Skipped: 0
```

La base guardaba una reserva `PAGADA`, un pago `APROBADO` y una mentoría sin dueño. La quinta prueba (los estados reales se aceptan) pasaba, como debe ser.

### 4.2 Pruebas automatizadas que cubren la regresión (`DataConstraintsMigrationTest`, 7)

| Prueba | Qué demuestra |
|---|---|
| `sampleOfferingsBelongToTheDemoProvider` | Ninguna mentoría queda sin proveedor y las 3 de ejemplo son de `proveedor.demo@skillbridge.dev` |
| `theExistingDemoProviderFromV4IsReused` | Se usa la cuenta que creó V4; no se duplica |
| `ifTheDemoProviderWasDeleted_itIsRecreatedWithoutAccess` | Si la cuenta no existe, se crea sin una contraseña válida |
| `anOfferingWithoutProviderIsRejected` | Insertar una mentoría sin `provider_id` falla |
| `anInventedBookingStatusIsRejected` | Una reserva `PAGADA` falla |
| `anInventedPaymentStatusIsRejected` | Un pago `APROBADO` falla |
| `everyRealStatusIsStillAccepted` | Todos los estados reales de reservas y pagos se siguen aceptando |

- `mvn verify`: **428 pruebas, 0 fallos**, 0 violaciones de Checkstyle y ArchUnit en verde. Que las mentorías de ejemplo ahora tengan un proveedor activo no cambió ninguna otra prueba.
- Pipeline de datos: `pytest` en su contenedor, **21 pruebas en verde** con el contrato actualizado.

## 5. Antes de desplegar

V12 **se detiene** si producción tiene estados inventados (el `CHECK` no se puede crear) o si `proveedor.demo@skillbridge.dev` dejó de ser `PROVIDER`. Para saberlo antes, en Neon:

```sql
SELECT 'bookings' AS tabla, status, COUNT(id) FROM bookings
WHERE status NOT IN ('CREATED','CONFIRMED','CANCELLED','COMPLETED') GROUP BY status
UNION ALL
SELECT 'payments', status, COUNT(id) FROM payments
WHERE status NOT IN ('PENDING','PAID','FAILED','CANCELED') GROUP BY status;

SELECT email, role, status FROM app_users WHERE email = 'proveedor.demo@skillbridge.dev';
```

Si la primera consulta no devuelve filas y la segunda muestra `PROVIDER`, V12 se aplica sin problemas.

## 6. Para las siguientes historias

- **HU-37:** si agrega estados (`PENDING_PAYMENT`, `EXPIRED`), su migración debe reemplazar `chk_bookings_status` (y `chk_payments_status` si toca pagos): `ALTER TABLE ... DROP CONSTRAINT ...; ALTER TABLE ... ADD CONSTRAINT ... CHECK (...)`.
- **HU-44 / HU-46 / HU-47 / HU-49:** ya no hace falta un caso para `provider_id` nulo.
