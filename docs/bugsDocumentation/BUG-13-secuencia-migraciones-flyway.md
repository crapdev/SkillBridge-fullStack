# BUG-13 — Renumeración de migraciones Flyway

[SCRUM-417](https://calborparra.atlassian.net/browse/SCRUM-417)

La secuencia queda en **V1–V13**. V1 y V2 conservan su nombre, V4–V13 pasan a V3–V12 y V20 pasa a V13. Los SQL conservan su contenido y checksum. La próxima versión es **V14**. Si dos PR agregan migraciones, coordina el orden de integración y reasigna las versiones pendientes sobre la rama destino antes del merge.

## Actualización de una base existente en Neon

1. Ensaya el procedimiento en una copia de producción. La operación requiere que estén aplicadas exactamente las 13 migraciones originales y que Flyway las valide con el artefacto anterior. Si se aplicaron scripts de relleno o el historial difiere, este procedimiento requiere revisión.
2. Programa una ventana de mantenimiento. Suspende el backend de Render y cualquier otro proceso que pueda iniciar Flyway sobre la base. Desactiva temporalmente los despliegues automáticos. Crea un respaldo o punto de restauración de Neon y guarda el historial original para la comparación.
3. Desde la raíz del repositorio ejecuta el script con una conexión PostgreSQL autorizada a modificar el historial. Usa la conexión directa de Neon, con TLS, y gestiona las credenciales fuera del repositorio.

   ```bash
   psql "$NEON_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f scripts/bug-13-renumber-flyway-history.sql
   ```

   El script comprueba nombres, versiones, orden y éxito de las migraciones. En una transacción cambia solo `version` y `script` en `public.flyway_schema_history`. Si falla, mantén el backend detenido hasta revisar el resultado.

4. Comprueba el historial con la consulta siguiente. Espera las versiones 1 a 13 en el mismo orden de ejecución, con los mismos checksums y rangos del respaldo. Despliega un artefacto nuevo construido desde cero, sin `SPRING_FLYWAY_OUT_OF_ORDER`. Comprueba que Flyway valida sin ejecutar migraciones y que el backend está saludable antes de reactivar el despliegue automático.
5. Si debes volver al artefacto anterior, detén primero el backend. El artefacto anterior requiere el historial original. Restaura el estado previo desde el respaldo según el procedimiento ensayado. Si hubo escrituras después del cambio, coordina su preservación antes de restaurar.

```sql
SELECT installed_rank, version, script, checksum, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

Para una base nueva basta desplegar el artefacto nuevo. Flyway aplica los trece SQL en orden.
