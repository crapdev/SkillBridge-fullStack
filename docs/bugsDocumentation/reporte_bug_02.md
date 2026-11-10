# Reporte de Incidencia: BUG-02 - La IA recomienda mentorías que no existen

| Campo | Detalle |
| :--- | :--- |
| **Identificador** | BUG-02 |
| **Historia de Usuario** | HU-25 / Asistente IA Conversacional |
| **Componentes Afectados** | Backend (`AiRecommendationService`, `DeepSeekAiAdapter`, `JpaOfferingRepository`) & Frontend (`AiComponent`) |
| **Severidad / Prioridad** | Alta / Bloqueante para lanzamiento comercial |
| **Estado** | Resuelto y Verificado |

---

## 1. Descripción del Problema

El asistente conversacional de inteligencia artificial (basado en DeepSeek e integrado mediante Spring AI) recomendaba mentorías inexistentes, inventando identificadores, nombres de sesiones y enlaces Markdown del tipo `[Título](/services/<id>)`.

Este comportamiento se presentaba de forma crítica en tres escenarios:
1. Cuando el catálogo visible en la base de datos estaba completamente vacío (sin mentorías activas o sin horarios disponibles).
2. Cuando las mentorías existentes en base de datos no contaban con horarios futuros libres en la tabla `availability_slots` (`scheduled_at > NOW()` y `reserved = false`).
3. Cuando el usuario solicitaba un tema técnico o tecnología que no guardaba ninguna relación con el catálogo disponible (el modelo forzaba o inventaba recomendaciones aproximadas para responder).

---

## 2. Pasos para Reproducir

1. **Configurar el entorno con catálogo vacío:**
   - Vaciar los registros de `availability_slots` o desactivar las mentorías activas en PostgreSQL.
2. **Interactuar con el asistente:**
   - Iniciar sesión en SkillBridge y navegar a la vista `/ai`.
   - Enviar una consulta en el chat: *"Recomiéndame una mentoría para aprender Spring Boot y arquitectura hexagonal"*.
3. **Observar la respuesta generada:**
   - La IA responde ofreciendo sesiones con enlaces como `[Spring Boot Pro](/services/12)`.
   - Al hacer clic en el enlace, la aplicación navega a una ruta inexistente `/services/12` arrojando un error 404.
4. **Prueba unitaria de regresión (QA):**
   - Configurar `when(offeringRepo.findVisible()).thenReturn(List.of())`.
   - Invocar `service.recommend("Aprender algo", List.of())`.
   - Verificar si el puerto `aiPort.recommend(...)` era invocado.

---

## 3. Comportamiento Esperado vs. Observado

| Escenario | Comportamiento Observado (Bug) | Comportamiento Esperado (Corregido) |
| :--- | :--- | :--- |
| **Catálogo vacío en base de datos** | El servicio invocaba a DeepSeek enviando `(no hay mentorías disponibles)`. El modelo alucinaba nombres de mentorías y UUIDs ficticios. | `AiRecommendationService` corta el flujo de inmediato sin invocar al modelo, devolviendo el mensaje de catálogo vacío. En el frontend se muestra el estado vacío con enlace a `/servicios`. |
| **Mentorías sin horarios futuros libres** | Se enviaban a la IA mentorías que no tenían horarios disponibles en `availability_slots`, permitiendo al usuario intentar reservar cupos inexistentes. | `findVisibleOfferings` filtra con cláusula `EXISTS` sobre `availability_slots` exigiendo `reserved = false AND scheduled_at > :now`. |
| **Tema no relacionado en el catálogo** | Al pedir tecnologías no cubiertas (ej. Cobol o Cocina), el modelo forzaba la recomendación inventando cursos afines. | El `SYSTEM_PROMPT` prohíbe explícitamente forzar o inventar recomendaciones y exige responder que no hay opciones disponibles. |
| **Interfaz de usuario (Frontend)** | Si el asistente no entregaba mentorías válidas, el usuario quedaba sin opciones claras de navegación. | `AiComponent` detecta la ausencia de mentorías y despliega un banner de estado vacío con el mensaje *"Por ahora no hay mentorias disponibles"* y enlace directo al catálogo `/servicios`. |

---

## 4. Análisis de Causa Raíz

1. **Falta de cortocircuito en la capa de aplicación (`AiRecommendationService`):**
   - No existía una condición de guarda `if (catalog.isEmpty())`. Aunque el repositorio devolvía cero registros, se construía el payload y se llamaba al modelo externo, incurriendo en consumo de tokens y exponiendo al usuario a alucinaciones.
2. **Ambigüedad en las reglas del System Prompt (`DeepSeekAiAdapter`):**
   - La instrucción previa decía *"Explica brevemente por qué encaja con lo que busca el cliente y propone un siguiente paso"*. Esto incentivaba al modelo generativo a buscar similitudes forzadas o inventar sesiones cuando el usuario preguntaba por temas ajenos al catálogo.
3. **Ausencia de estado vacío dedicado en Frontend (`AiComponent`):**
   - La vista del chat renderizaba únicamente el Markdown en HTML sin validar si la respuesta contenía enlaces de mentorías reales, careciendo de un llamado a la acción (`Call To Action`) que guiara al usuario hacia el catálogo general.

---

## 5. Solución Implementada

### A. Capa de Aplicación (Backend)
- Se añadió retorno temprano en `AiRecommendationService.recommend()`:
  ```java
  List<Offering> catalog = offerings.findVisible();
  if (catalog.isEmpty()) {
      return EMPTY_CATALOG_MESSAGE;
  }
  ```
- Se garantizó que el puerto `AiRecommendationPort` jamás sea invocado si el catálogo visible está vacío.

### B. Capa de Infraestructura - IA (Backend)
- Se fortaleció `SYSTEM_PROMPT` en `DeepSeekAiAdapter`:
  - Solo se permite recomendar mentorías con relación temática directa.
  - Prohibición estricta de forzar o inventar títulos, IDs o enlaces Markdown.
  - Obligación de indicar con claridad que no hay opciones adecuadas cuando ninguna coincide.

### C. Capa de Persistencia (Backend)
- Se verificó y testeó la consulta JPQL con subconsulta `EXISTS`:
  ```sql
  AND EXISTS (
    SELECT 1 FROM AvailabilitySlotEntity s
    WHERE s.offeringId = o.id
      AND s.reserved = false
      AND s.scheduledAt > :now
  )
  ```
- Se implementó la prueba de integración con PostgreSQL real en Testcontainers (`JpaOfferingRepositoryTest`).

### D. Interfaz de Usuario (Frontend Angular)
- En `AiComponent`, se incorporó el método `hasOfferings(content: string)` para detectar si la respuesta del asistente incluye enlaces válidos al catálogo (`/services/<id>`).
- En `ai.html`, se añadió el bloque condicional que muestra:
  > **Por ahora no hay mentorias disponibles**  
  > [Explorar catálogo de mentorías →](https://www.google.com/search?q=/servicios)
- En `ai.component.spec.ts`, se cubrieron las pruebas unitarias para validar la presencia del estado vacío y el enlace al catálogo.

---

## 6. Evidencia de Pruebas y Validación

### Evidencia de Fallo Previo (QA)
- **Test:** `AiRecommendationServiceTest.shouldNotCallAiWhenCatalogIsEmpty`
- **Resultado antes del fix:** `AssertionError: NeverWantedButInvoked: aiRecommendationPort.recommend(...) Actual invocations: 1`
- **Diagnóstico:** Confirmación de que el servicio consultaba a la IA aun con lista vacía.

### Evidencia de Éxito Posterior (All Green)
1. **Compilación y Checkstyle:**
   ```
   [INFO] --- maven-checkstyle-plugin:3.6.0:check (checkstyle-unused-imports) ---
   [INFO] BUILD SUCCESS
   ```
2. **Pruebas Unitarias de Aplicación e IA:**
   ```
   [INFO] Running com.riwi.skillbridge.application.service.AiRecommendationServiceTest
   [INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 - in AiRecommendationServiceTest
   [INFO] Running com.riwi.skillbridge.infrastructure.adapter.out.ai.DeepSeekAiAdapterTest
   [INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 - in DeepSeekAiAdapterTest
   ```
3. **Prueba de Integración de Persistencia:**
   ```
   [INFO] Running com.riwi.skillbridge.infrastructure.adapter.out.persistence.JpaOfferingRepositoryTest
   [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 - in JpaOfferingRepositoryTest
   ```
4. **Pruebas del Frontend (Karma / Jasmine):**
   ```
   Chrome Headless: Executed 13 of 13 specs SUCCESS
   - debe mostrar "Por ahora no hay mentorias disponibles" con enlace al catálogo cuando la respuesta no trae mentorías recomendadas
   - no debe mostrar el estado vacío cuando la respuesta sí incluye enlaces a mentorías del catálogo
   ```
