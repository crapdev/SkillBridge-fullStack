# BUG-06 · Cancelar una reserva no libera el horario

Jira: [SCRUM-382](https://calborparra.atlassian.net/browse/SCRUM-382) · Relacionado: BUG-07 ([SCRUM-383](https://calborparra.atlassian.net/browse/SCRUM-383))

## 1. Reporte

**Pasos para reproducir**
1. Como cliente, reservar un horario con más de 24 horas de anticipación.
2. Cancelar la reserva desde *Mis reservas*.
3. Revisar `availability_slots.reserved` de esa franja y el catálogo de horarios de la mentoría.

**Esperado:** la franja vuelve a `reserved = false` y aparece de nuevo como disponible.
**Observado:** la franja se queda en `reserved = true` y no se muestra en el catálogo. Si era el único horario libre, la mentoría completa desaparece del catálogo.

## 2. Causa raíz

`BookingService.create` marca la franja como reservada (`reserved = true`), pero **no existía el camino inverso**:

1. `BookingService.cancelBooking` solo guardaba la reserva con estado `CANCELLED`; nunca tocaba el `AvailabilitySlot`.
2. `listAvailableSlots` solo muestra franjas con `reserved = false`, así que la franja cancelada no volvía a ofrecerse.
3. El catálogo (`findVisible`) solo lista mentorías con al menos un horario futuro libre, y se guarda en caché (Redis). Aunque la franja se liberara, la caché seguiría sin la mentoría.

**Efecto en los datos**

- **Ocupación inflada (HU-45):** cada cancelación dejaba una franja `reserved = true` sin una reserva activa detrás, así que la ocupación de los proveedores salía más alta de lo real.
- **Falsos errores de calidad (HU-45):** la regla "franja reservada sin reserva activa" saltaba con cada cancelación real, aunque no hubiera ningún problema en los datos.
- Las franjas que quedaron bloqueadas antes de este arreglo **no se corrigen solas**: el cambio solo aplica a las cancelaciones nuevas.

## 3. Solución

| Capa | Cambio |
|---|---|
| Aplicación | `BookingService.cancelBooking`, después de que `Booking.cancel()` acepta la cancelación, busca la franja de esa mentoría y esa hora y la guarda con `reserved = false` |
| Transacción | `cancelBooking` es `@Transactional`: la reserva `CANCELLED` y la franja liberada se guardan juntas o no se guarda ninguna |
| Caché | Al liberar la franja se llama a `OfferingCachePort.evictActiveOfferings()`, para que la mentoría vuelva a aparecer en el catálogo |
| Casos borde | Si la cancelación se rechaza (menos de 24 horas, reserva ajena, ya cancelada o completada) se lanza la excepción **antes** de tocar la franja. Si el proveedor ya borró la franja, la cancelación se completa igual y no hay nada que liberar |

No hace falta migración Flyway: la columna `reserved` ya existe.

## 4. Evidencia

### 4.1 Test que demuestra el fallo antes de corregirlo

La prueba `shouldReleaseSlotWhenBookingIsCancelled` se escribió primero (SCRUM-391) y falló porque `cancelBooking` ni siquiera consultaba el repositorio de franjas, que es justamente la causa raíz:

```text
[ERROR] BookingServiceTest.shouldReleaseSlotWhenBookingIsCancelled:173
Wanted but not invoked:
Actually, there were zero interactions with this mock.
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
```

### 4.2 Pruebas automatizadas que cubren la regresión (`BookingServiceTest`)

| Prueba | Qué demuestra |
|---|---|
| `shouldReleaseSlotWhenBookingIsCancelled` | Cancelar con más de 24 horas guarda la franja con `reserved = false` e invalida la caché del catálogo |
| `shouldCancelBookingEvenWhenSlotNoLongerExists` | Si la franja ya no existe, la reserva queda `CANCELLED` sin error |
| `shouldNotReleaseSlotWhenCancellationIsRejectedByTimeWindow` | Con menos de 24 horas la cancelación se rechaza y la franja no se toca |
| `shouldNotReleaseSlotWhenCancellingAnotherUsersBooking` | Cancelar una reserva ajena se rechaza y la franja no se toca |
| `shouldNotReleaseSlotWhenBookingIsAlreadyCancelled` | Cancelar dos veces se rechaza y la franja no se toca |

Resultado de `mvn verify` en la rama: **361 pruebas, 0 fallos**, 0 violaciones de Checkstyle y ArchUnit en verde. Las 9 pruebas de persistencia con Testcontainers necesitan Docker encendido; ninguna cubre este cambio.

## 5. Relación con BUG-07

Este arreglo deja la franja en `reserved = false`, pero **todavía no se puede volver a reservar**: `BookingService.create` también revisa `existsByOfferingIdAndScheduledAt` y `existsByCustomerIdAndScheduledAt`, que cuentan las reservas `CANCELLED`. Eso es BUG-07. Hacen falta los dos arreglos para que un horario cancelado vuelva a venderse.

## 6. Para las siguientes historias

- **HU-37:** su criterio "Cancelar una reserva libera el horario" ya queda cubierto. Si la reserva con tiempo límite expira, debe liberar la franja igual que `cancelBooking`.
- **HU-45:** la regla "franja reservada sin reserva activa" deja de fallar con cada cancelación real.
