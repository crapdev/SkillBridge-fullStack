# Despliegue

Cómo se publica SkillBridge y qué configurar en cada servicio. Para la lista de verificación antes y después de publicar, ver [DEPLOYMENT-CHECKLIST.md](DEPLOYMENT-CHECKLIST.md). Para el detalle de la sesión, ver [AUTHENTICATION.md](AUTHENTICATION.md).

---

## 1. Topología

```text
Navegador
   |
   |  https://<app>.vercel.app/...        (Angular)
   |  https://<app>.vercel.app/api/...    (misma página, mismo origen)
   v
Vercel  --- rewrite /api/:path* --->  Render (Spring Boot, Docker)
                                         |
                       +-----------------+------------------+-----------+
                       v                 v                  v           v
                 PostgreSQL (Neon)  Redis (Upstash)  RabbitMQ (CloudAMQP)  DeepSeek / Stripe
```

**Regla clave: el navegador solo habla con el dominio de Vercel.** Vercel reenvía `/api/**` a Render (`frontend/vercel.json`), así que para el navegador la API está en el mismo origen que la página.

Esto es obligatorio desde HU-29: la sesión es una cookie `SameSite=Strict` con `Path=/api`. Si Angular llamara directo a `https://<backend>.onrender.com`, la cookie quedaría en otro sitio y el navegador no la enviaría: el login "funcionaría" pero cada petición siguiente respondería `401`.

---

## 2. Backend en Render

1. **New → Web Service**, conecta el repositorio.
2. Configuración:

   ```text
   Root Directory: backend
   Runtime:        Docker          (usa backend/Dockerfile)
   Health Check:   /actuator/health/liveness
   ```

   Se usa **liveness** para que una caída momentánea de Redis o RabbitMQ no reinicie el proceso. `/actuator/health` sigue sirviendo para diagnosticar dependencias.

3. Variables de entorno:

   | Variable | Obligatoria | Valor |
   |---|---|---|
   | `DATABASE_URL` | Sí | `jdbc:postgresql://<host>/<db>?sslmode=require` |
   | `DATABASE_USER` / `DATABASE_PASSWORD` | Sí | Credenciales de Neon |
   | `REDIS_URL` | Sí | `rediss://...` (Upstash, con TLS) |
   | `RABBITMQ_URL` | Sí | `amqps://...` (CloudAMQP) |
   | `JWT_SECRET` | Sí | Base64 aleatorio ≥ 256 bits: `openssl rand -base64 48` |
   | `JWT_EXPIRATION` | No | Duración de la sesión en ms. Por defecto `28800000` (8 h) |
   | `AUTH_COOKIE_SECURE` | No | Dejar sin definir (`true`). **Nunca `false` en producción** |
   | `CORS_ALLOWED_ORIGINS` | Sí | `https://<app>.vercel.app` (más previews si se usan, separadas por coma) |
   | `DEEPSEEK_API_KEY` | Para IA | API key de DeepSeek. Solo en el backend |
   | `DEEPSEEK_MODEL` / `DEEPSEEK_MAX_TOKENS` | No | Por defecto `deepseek-chat` / `800` |
   | `STRIPE_SECRET_KEY` | Para pagos | `sk_...` |
   | `STRIPE_WEBHOOK_SECRET` | Para pagos | `whsec_...` del endpoint del paso 5 |
   | `DB_POOL_SIZE` | No | Por defecto `5` (los planes gratuitos limitan conexiones) |
   | `OFFERINGS_CACHE_TTL_MINUTES` | No | Por defecto `10` |

   `PORT` lo pone Render; Spring lo lee con `server.port: ${PORT:8080}`.

4. Deploy. Flyway aplica las migraciones al arrancar. Comprueba:

   ```text
   https://<backend>.onrender.com/actuator/health
   ```

5. **Webhook de Stripe** (si se usan pagos): en el dashboard de Stripe crea un endpoint hacia
   `https://<backend>.onrender.com/api/payments/webhook` y copia su `whsec_...` en `STRIPE_WEBHOOK_SECRET`. Stripe llama directo a Render; no pasa por Vercel ni necesita sesión ni CSRF (se valida con `Stripe-Signature`).

> **Render Free** suspende el servicio por inactividad: la primera petición tras un rato puede tardar cerca de un minuto (cold start). Sirve para demos, no para disponibilidad continua.

---

## 3. Frontend en Vercel

1. **Add New → Project**, importa el mismo repositorio.
2. `Root Directory: frontend`. `vercel.json` ya define el build (`npm run build`), la carpeta de salida y los rewrites.
3. **Apunta el rewrite a tu backend.** En `frontend/vercel.json`:

   ```json
   { "source": "/api/:path*", "destination": "https://<backend>.onrender.com/api/:path*" }
   ```

   La URL de Render está escrita en el archivo: si cambia el backend, hay que actualizarla y volver a desplegar.

4. Variables de entorno (las lee `scripts/generate-env.mjs` en el build y genera `public/env.js`):

   | Variable | Valor |
   |---|---|
   | `API_URL` | **No definir** (queda `/api`, que pasa por el rewrite) |
   | `STRIPE_PUBLISHABLE_KEY` | `pk_...` (es pública por diseño; nunca la `sk_...`) |

   > Las instrucciones antiguas pedían `API_URL=https://<backend>.onrender.com/api`. Con la sesión en cookie eso rompe el login (ver sección 1).

5. Deploy y vuelve a Render para confirmar que `CORS_ALLOWED_ORIGINS` tiene la URL final de Vercel.

---

## 4. Alternativa: frontend en contenedor (Nginx)

`frontend/Dockerfile` construye Angular y lo sirve con Nginx, que hace de proxy de `/api/` hacia `BACKEND_URL` (`nginx.conf.template`). Cumple la misma regla de mismo origen que el rewrite de Vercel.

Para desplegarlo como segundo Web Service en Render:

```text
Root Directory: frontend
Runtime:        Docker
BACKEND_URL=https://<backend>.onrender.com
```

Consume más horas de cómputo que Vercel; úsalo solo si quieres mostrar Nginx en la nube.

---

## 5. Entorno local con Docker Compose

Crea un `.env` en la raíz con al menos `JWT_SECRET` (y las keys de DeepSeek o Stripe si las vas a usar; ver la sección 7 del README) y levanta todo:

```bash
docker compose up -d --build
```

| URL | Servicio |
|---|---|
| `http://localhost:8088` | Frontend (Nginx → `/api` al backend) |
| `http://localhost:8080` | Backend directo (Swagger en `/swagger-ui.html`) |
| `http://localhost:15672` | Consola de RabbitMQ |

Diferencias con producción, definidas en `docker-compose.yml`:

- `AUTH_COOKIE_SECURE=false`: en `http://localhost` no hay HTTPS y la cookie no se guardaría.
- `JWT_EXPIRATION` se puede acortar en `.env` para probar la expiración de la sesión.

Para desarrollar Angular con recarga en caliente usa `npm start` en `frontend/` (puerto 4200); `proxy.conf.json` reenvía `/api` a `http://localhost:8080`, igual que el rewrite de Vercel.

---

## 6. CI

`.github/workflows/ci.yml` corre en cada push a `main`, `develop`, `feature/**` y `fix/**`, y en los PR hacia `main` y `develop`:

- **Backend**: Checkstyle (imports sin usar), reglas de arquitectura hexagonal y `mvn verify` (tests con Testcontainers).
- **Frontend**: `npm ci`, chequeo de tipos (`tsc`) y build de producción. Los tests de Angular (`ng test`) no corren en CI: ejecútalos en local antes del PR.

Render y Vercel despliegan solos al hacer push a la rama configurada en cada servicio. Configúralos sobre `main` para que solo se publique lo que pasó por PR y CI.

---

## 7. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| El login responde 200 pero todo lo demás da `401` | Angular llama directo a Render (`API_URL` definido) o falta el rewrite en `vercel.json` |
| `401` en todas las peticiones en local con Docker | `AUTH_COOKIE_SECURE` en `true` con `http://localhost` |
| `403` al guardar o borrar (no al leer) | Falta la cabecera `X-XSRF-TOKEN`: la petición no salió por el `HttpClient` de Angular |
| Error de CORS en la consola | La URL de Vercel no está en `CORS_ALLOWED_ORIGINS` |
| El backend no arranca: `UnknownHostException: postgres` | Contenedor desconectado de la red de Compose: `docker compose up -d --force-recreate backend` |
| La primera petición tarda ~1 minuto | Cold start de Render Free |
