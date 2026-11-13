# Autenticación y autorización

Cómo inicia sesión un usuario, dónde vive la sesión, qué protege a cada petición y qué ve el usuario cuando la sesión expira.

---

## 1. Resumen

| Tema | Decisión |
|---|---|
| Credencial | JWT firmado con HMAC (`JWT_SECRET`) |
| Dónde viaja | Cookie `sb_token`: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api` |
| Duración | `JWT_EXPIRATION` en milisegundos; por defecto **8 horas**. Sin refresh token |
| Estado en el servidor | Ninguno (`STATELESS`): cada petición se valida con el JWT |
| CSRF | Cookie legible `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN` (Angular la envía sola) |
| Contraseñas | BCrypt |
| Alta de cuentas | Verificación de correo (HU #39) en el microservicio NestJS `email-service`, bajo `/api/verification/**`. Login, `/auth/me` y logout siguen en Spring Boot |
| Roles | `CUSTOMER`, `PROVIDER`, `ADMIN` |

El JavaScript de la página **nunca ve el token**: Angular solo sabe quién es el usuario preguntándole al backend (`GET /api/auth/me`). Así, un XSS no puede robar la sesión.

---

## 2. Flujo de inicio de sesión

```text
Angular                                   Spring Boot
   |  POST /api/auth/login {email,password}    |
   |------------------------------------------>|  valida contraseña (BCrypt)
   |                                           |  revisa estado de la cuenta
   |  200 {email,name,role}                    |  genera JWT (email + rol)
   |  Set-Cookie: sb_token=<jwt>; HttpOnly...  |
   |<------------------------------------------|
   |                                           |
   |  GET /api/provider/stats                  |
   |  Cookie: sb_token=<jwt>   (automático)    |
   |------------------------------------------>|  JwtAuthenticationFilter valida el JWT
   |  200 {...}                                |  y carga usuario + rol
   |<------------------------------------------|
```

Al arrancar la app, `AuthService.restore()` llama a `GET /api/auth/me`: si la cookie sigue vigente recupera al usuario; si responde 401 la app arranca como visitante.

### Endpoints

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `POST /api/verification/register` | Público | `202 {email,message}`. **No** abre sesión. Lo atiende el `email-service` (ver sección 3) |
| `POST /api/verification/register/provider` | Público | `202 {email,message}`. **No** abre sesión. Lo atiende el `email-service` |
| `POST /api/auth/login` | Público | `200 {email,name,role}` + cookie |
| `GET /api/auth/me` | Con sesión | `200 {email,name,role}` |
| `POST /api/auth/logout` | Público | `204` + cookie vacía con `Max-Age=0` |

`logout` es público a propósito: permite salir aunque el token ya haya expirado.

`POST /api/auth/register` y `POST /api/auth/register/provider` **ya no existen** en Spring Boot: su reemplazo es `/api/verification/register` y `/register/provider`. El resto de `/api/verification/**` (`verify`, `resend`, `status`) está en la sección 3 y en [API.md](API.md).

### Estado de la cuenta al iniciar sesión

El estado se revisa **después** de validar la contraseña, para no revelar qué correos existen.

| Situación | Respuesta |
|---|---|
| Correo inexistente o contraseña errónea | `401` "Email inexistente o contrasena erronea" |
| Cuenta sin verificar el correo | `401`, igual que si no existiera (vive en `email.pending_registrations`, no en `app_users`) |
| Proveedor pendiente de aprobación | `403` |
| Solicitud de proveedor rechazada | `403` |
| Cuenta desactivada por un admin | `403` |

Los errores siguen RFC 7807 (`ProblemDetail`); el frontend muestra el campo `detail`.

---

## 3. Verificación de correo (HU #39)

Registrarse ya no abre sesión: la cuenta queda **pendiente** hasta que el usuario abre el enlace que le llega por correo. Lo atiende `email-service/` (NestJS, raíz del repo), con la misma Postgres y el esquema `email`.

La cuenta sin verificar vive en `email.pending_registrations`, **no** en `app_users`. Al verificar, Nest crea el usuario en `app_users` y, si es cliente, abre la sesión. Después todo sigue en Spring Boot (`/auth/login`, `/auth/me`, logout), que no cambia.

### Flujo

1. `/login?mode=register` → `POST /api/verification/register` (o `/register/provider`) → `202`. El frontend muestra "Revisa tu correo", sin sesión.
2. Llega un correo con el enlace `/verificar-correo?token=...`, válido **24 h**.
3. La pantalla `verificar-correo` llama a `POST /api/verification/verify`:
   - **Cliente** → `200 {email,name,role}` + cookie `sb_token`. Va a `/ai` ya con sesión.
   - **Proveedor** → `202 {status: "PENDING_APPROVAL"}`, sin cookie. Su correo queda verificado, pero un admin debe aprobarlo (ver "Cómo se obtiene cada rol").
4. Enlace con problemas → `400` con `reason`, y la pantalla ofrece pedir otro:

| `reason` | Qué muestra el frontend |
|---|---|
| `TOKEN_EXPIRED` | Enlace vencido: pedir otro |
| `TOKEN_USED` | "Tu correo ya estaba verificado": ir a iniciar sesión |
| `TOKEN_INVALID` | Enlace no válido: pedir otro |

5. Reenvío: `POST /api/verification/resend {email}` responde siempre `202` con el mismo mensaje, exista o no el correo. Máximo 3 por correo cada 15 min (`429` si se pasa). Pedir un enlace nuevo **invalida el anterior**.
6. Registrarse otra vez con un correo que ya está pendiente reemplaza el registro y reenvía el correo (`202`). Si el correo ya está en `app_users`, `422`.

### Login con una cuenta sin verificar

Spring Boot no conoce la cuenta, así que responde `401` igual que con una contraseña errónea. Para no dejar al usuario sin explicación, el login, tras un `401`, llama a `POST /api/verification/status {email,password}`: si `pending: true`, muestra el aviso "Tu correo no está verificado" con botón de reenviar (cuenta atrás de 60 s). Si el `email-service` no responde, se muestra el error normal. `status` pide la contraseña y `resend` responde siempre igual para que nadie pueda averiguar qué correos están registrados.

### Por qué Nest emite la misma cookie

Para que el cliente quede con sesión nada más verificar, sin pasar otra vez por el login, el `email-service` emite la **misma** cookie `sb_token` que Spring Boot: JWT HS256 firmado con el mismo `JWT_SECRET`, `sub` = correo, `role`, 8 h; `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api`. Spring Boot la valida sin saber quién la creó. Por eso el navegador solo habla con un dominio y el proxy reparte `/api/...` (sección 8).

---

## 4. Roles y rutas

Reglas en `SecurityConfiguration` (gana la primera que coincide):

| Rutas | Acceso |
|---|---|
| `GET /api/auth/me` | Autenticado |
| `/api/auth/**`, `/actuator/health/**`, Swagger | Público |
| `GET /api/offerings/**` | Público (catálogo) |
| `POST /api/payments/webhook` | Público: se autentica con la firma `Stripe-Signature` |
| `/api/bookings/**`, `/api/payments/**`, `/api/ai/**` | `CUSTOMER` |
| `/api/provider/**`, `/api/notifications/**` | `PROVIDER` |
| `/api/admin/**` | `ADMIN` |
| Todo lo demás | Autenticado |

Además, cada servicio filtra por el usuario del token: un proveedor solo ve sus mentorías y reservas, aunque cambie un id en la URL.

En el frontend, `roleGuard` (`customerGuard`, `providerGuard`, `adminGuard`) protege las rutas de Angular. **Es solo UX**: quien autoriza de verdad es el backend.

Al iniciar sesión sin `returnUrl`, cada rol va a su pantalla: `ADMIN` → `/admin`, `CUSTOMER` → `/ai`, el resto → `/`.

### Cómo se obtiene cada rol

- **CUSTOMER**: registro (`/api/verification/register`) y **verificación del correo**; al verificar se crea la cuenta activa y se abre la sesión.
- **PROVIDER**: registro (`/api/verification/register/provider`) y **verificación del correo**; al verificar, la cuenta queda en `PENDING_APPROVAL` y un admin la aprueba desde el panel (sin aprobar no puede iniciar sesión: `403`).
- **ADMIN**: lo crea la migración `V5__seed_admin.sql`. No hay registro público de administradores.

---

## 5. Protección CSRF

Como el navegador envía la cookie de sesión solo, una página ajena podría intentar disparar peticiones con ella. Hay dos defensas:

1. **`SameSite=Strict`**: el navegador no envía `sb_token` en peticiones que vienen de otro sitio.
2. **Token CSRF**: en cada respuesta el backend escribe la cookie legible `XSRF-TOKEN`. En los `POST/PUT/PATCH/DELETE`, Angular copia su valor en la cabecera `X-XSRF-TOKEN` (`withXsrfConfiguration` en `main.ts`) y Spring compara ambos.

No se exige CSRF en:

- `POST /api/auth/**`: no actúan sobre una sesión existente y la primera visita aún no tiene `XSRF-TOKEN`.
- `POST /api/payments/webhook`: lo llama el servidor de Stripe.
- Peticiones con `Authorization: Bearer`: el navegador nunca agrega esa cabecera por su cuenta.
- Peticiones sin cookie de sesión: no hay sesión que aprovechar (así reciben `401` y no `403`).

---

## 6. Expiración de la sesión

El JWT y la cookie duran lo mismo (`JWT_EXPIRATION`). No hay renovación automática: al cumplirse el tiempo el usuario debe volver a iniciar sesión.

Qué pasa en el frontend:

1. Una petición recibe `401` con la sesión abierta.
2. `authInterceptor` cierra la sesión local, borra los datos guardados del usuario y redirige a `/login?expired=1&returnUrl=<página actual>`.
3. El login muestra una ventana: **"Tu sesión expiró"**.
4. Al volver a ingresar, el usuario regresa a `returnUrl` (solo se aceptan rutas internas, para evitar redirecciones abiertas).

Excepciones: los `401` de `/api/auth/**` (credenciales inválidas) y las peticiones marcadas con `KEEP_SESSION_ON_401` no cierran la sesión.

### Probar la expiración en local

`docker-compose.yml` permite acortar la sesión. Define en `.env`:

```env
JWT_EXPIRATION=300000   # 5 minutos
```

Recrea el backend (`docker compose up -d --force-recreate backend`), inicia sesión, espera y navega a una página que pida datos.

---

## 7. Probar la API con curl o Swagger

El login ya no devuelve el token en el cuerpo. Para usar la API fuera del navegador, guarda las cookies y usa el JWT como Bearer (con Bearer no se exige CSRF):

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"Password123"}'

TOKEN=$(awk '$6=="sb_token"{print $7}' cookies.txt)

curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

Si llegan las dos cosas, la cabecera `Authorization` tiene prioridad sobre la cookie.

El usuario del ejemplo debe existir ya. Para crear uno, el registro pasa ahora por el `email-service` (puerto 3000 directo, o `/api/verification/...` a través del proxy del frontend) y hay que abrir el enlace del correo (en local, Mailpit) antes de iniciar sesión:

```bash
curl -X POST http://localhost:3000/api/verification/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com","password":"Password123"}'
# 202 {"email":"ada@example.com","message":"..."}

curl -c cookies.txt -X POST http://localhost:3000/api/verification/verify \
  -H 'Content-Type: application/json' -d '{"token":"<token del enlace>"}'
```

### Probar la verificación sin backend (mock)

Con `ng serve` (solo modo desarrollo) el frontend puede simular el `email-service`. En la consola del navegador:

```js
localStorage.setItem('skillbridge_verification_mock', 'on')   // y recargar
```

| Para probar | Usa |
|---|---|
| Cliente verificado (`200` + sesión) | `/verificar-correo?token=token-valido` |
| Proveedor pendiente de aprobación (`202`) | `token-proveedor` |
| Enlace vencido / ya usado | `token-vencido` / `token-usado` |
| Enlace inválido | cualquier otro token, o sin token |
| Correo ya registrado (`422`) | registrarse con `existente@skillbridge.dev` |

Al registrarse, el enlace del "correo" se escribe en la consola del navegador. Limitaciones: los datos viven en memoria (se pierden al recargar) y el mock no puede crear la cookie `HttpOnly`, así que tras verificar, recargar la página cierra la sesión. Para apagarlo: `localStorage.removeItem('skillbridge_verification_mock')`.

---

## 8. Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `JWT_SECRET` | Clave de desarrollo | Firma del JWT. **Obligatorio cambiarla en producción** (Base64, ≥ 256 bits: `openssl rand -base64 48`) |
| `JWT_EXPIRATION` | `28800000` (8 h) | Duración del JWT y de la cookie, en ms |
| `AUTH_COOKIE_SECURE` | `true` | Cookie solo por HTTPS. `docker-compose.yml` la pone en `false` porque en local se usa `http://localhost` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8088,http://localhost:4200` | Orígenes del frontend, separados por coma |
| `EMAIL_SERVICE_URL` | `http://email-service:3000` | Solo el contenedor del frontend (nginx): destino de `/api/verification/` |

### Rutas hacia el email-service

Ahora hay dos servidores detrás de `/api`. En los tres entornos, `/api/verification` se enruta **antes** que la regla general de `/api`:

| Entorno | Archivo | Destino de `/api/verification` |
|---|---|---|
| `ng serve` | `frontend/proxy.conf.json` | `http://localhost:3000` (este proxy usa la primera regla que coincide, por eso va primero) |
| Vercel | `frontend/vercel.json` | `https://skillbridge-email-service.onrender.com`. **URL provisional**: confirmar al desplegar el `email-service` |
| Docker | `frontend/nginx.conf.template` | `${EMAIL_SERVICE_URL}`. El `Dockerfile` la define y la añade a `NGINX_ENVSUBST_FILTER=BACKEND_URL\|EMAIL_SERVICE_URL`; sin eso nginx no sustituye la variable |

En nginx el destino va en una variable con `resolver 127.0.0.11` (DNS de Docker): así nginx busca el host en cada petición y el frontend arranca aunque no exista el contenedor `email-service`; en ese caso solo `/api/verification` responde `502`.

---

## 9. Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `backend/.../config/SecurityConfiguration.java` | Reglas de acceso, CSRF, CORS |
| `backend/.../security/JwtAuthenticationFilter.java` | Lee el JWT (Bearer o cookie) y autentica la petición |
| `backend/.../security/JwtService.java` | Genera y valida JWT |
| `backend/.../security/AuthCookies.java` | Atributos de la cookie `sb_token` |
| `backend/.../security/SpaCsrfTokenRequestHandler.java` | CSRF para una SPA |
| `backend/.../application/service/AuthService.java` | Login y estados de cuenta (el registro pasó al `email-service`) |
| `frontend/src/app/core/auth.service.ts` | Usuario de la sesión, login, logout y verificación de correo (`register`, `registerProvider`, `verifyEmail`, `resendVerification`, `verificationStatus`) |
| `frontend/src/app/features/verify-email/` | Pantalla `/verificar-correo?token=` |
| `frontend/src/app/core/verification/` | Mock de desarrollo del `email-service` (interruptor + interceptor) |
| `email-service/` | Microservicio NestJS: registro pendiente, verificación, reenvío y estado |
| `frontend/src/app/core/auth.interceptor.ts` | Cierra la sesión ante un `401` |
| `frontend/src/app/core/role.guard.ts` | Guards de rutas por rol |

---

## 10. Pendientes conocidos

- **Renovación de sesión** (sliding session o refresh token): permitiría bajar la expiración sin sacar a usuarios activos.
- **Límite de intentos de login** (rate limiting / bloqueo por fuerza bruta).
- **Revocación**: hoy un JWT robado sigue siendo válido hasta que expira; cerrar sesión solo borra la cookie de ese navegador.
