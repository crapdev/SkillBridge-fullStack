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
| `POST /api/auth/register` | Público | `201` + cookie. Crea un `CUSTOMER` activo |
| `POST /api/auth/register/provider` | Público | `202 {status: "PENDING_APPROVAL"}`. **No** abre sesión |
| `POST /api/auth/login` | Público | `200 {email,name,role}` + cookie |
| `GET /api/auth/me` | Con sesión | `200 {email,name,role}` |
| `POST /api/auth/logout` | Público | `204` + cookie vacía con `Max-Age=0` |

`logout` es público a propósito: permite salir aunque el token ya haya expirado.

### Estado de la cuenta al iniciar sesión

El estado se revisa **después** de validar la contraseña, para no revelar qué correos existen.

| Situación | Respuesta |
|---|---|
| Correo inexistente o contraseña errónea | `401` "Email inexistente o contrasena erronea" |
| Proveedor pendiente de aprobación | `403` |
| Solicitud de proveedor rechazada | `403` |
| Cuenta desactivada por un admin | `403` |

Los errores siguen RFC 7807 (`ProblemDetail`); el frontend muestra el campo `detail`.

---

## 3. Roles y rutas

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

- **CUSTOMER**: registro normal.
- **PROVIDER**: `register/provider` deja la cuenta en `PENDING_APPROVAL`; un admin la aprueba desde el panel.
- **ADMIN**: lo crea la migración `V5__seed_admin.sql`. No hay registro público de administradores.

---

## 4. Protección CSRF

Como el navegador envía la cookie de sesión solo, una página ajena podría intentar disparar peticiones con ella. Hay dos defensas:

1. **`SameSite=Strict`**: el navegador no envía `sb_token` en peticiones que vienen de otro sitio.
2. **Token CSRF**: en cada respuesta el backend escribe la cookie legible `XSRF-TOKEN`. En los `POST/PUT/PATCH/DELETE`, Angular copia su valor en la cabecera `X-XSRF-TOKEN` (`withXsrfConfiguration` en `main.ts`) y Spring compara ambos.

No se exige CSRF en:

- `POST /api/auth/**`: no actúan sobre una sesión existente y la primera visita aún no tiene `XSRF-TOKEN`.
- `POST /api/payments/webhook`: lo llama el servidor de Stripe.
- Peticiones con `Authorization: Bearer`: el navegador nunca agrega esa cabecera por su cuenta.
- Peticiones sin cookie de sesión: no hay sesión que aprovechar (así reciben `401` y no `403`).

---

## 5. Expiración de la sesión

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

## 6. Probar la API con curl o Swagger

El login ya no devuelve el token en el cuerpo. Para usar la API fuera del navegador, guarda las cookies y usa el JWT como Bearer (con Bearer no se exige CSRF):

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"Password123"}'

TOKEN=$(awk '$6=="sb_token"{print $7}' cookies.txt)

curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

Si llegan las dos cosas, la cabecera `Authorization` tiene prioridad sobre la cookie.

---

## 7. Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `JWT_SECRET` | Clave de desarrollo | Firma del JWT. **Obligatorio cambiarla en producción** (Base64, ≥ 256 bits: `openssl rand -base64 48`) |
| `JWT_EXPIRATION` | `28800000` (8 h) | Duración del JWT y de la cookie, en ms |
| `AUTH_COOKIE_SECURE` | `true` | Cookie solo por HTTPS. `docker-compose.yml` la pone en `false` porque en local se usa `http://localhost` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8088,http://localhost:4200` | Orígenes del frontend, separados por coma |

---

## 8. Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `backend/.../config/SecurityConfiguration.java` | Reglas de acceso, CSRF, CORS |
| `backend/.../security/JwtAuthenticationFilter.java` | Lee el JWT (Bearer o cookie) y autentica la petición |
| `backend/.../security/JwtService.java` | Genera y valida JWT |
| `backend/.../security/AuthCookies.java` | Atributos de la cookie `sb_token` |
| `backend/.../security/SpaCsrfTokenRequestHandler.java` | CSRF para una SPA |
| `backend/.../application/service/AuthService.java` | Registro, login y estados de cuenta |
| `frontend/src/app/core/auth.service.ts` | Usuario de la sesión, login, logout |
| `frontend/src/app/core/auth.interceptor.ts` | Cierra la sesión ante un `401` |
| `frontend/src/app/core/role.guard.ts` | Guards de rutas por rol |

---

## 9. Pendientes conocidos

- **Renovación de sesión** (sliding session o refresh token): permitiría bajar la expiración sin sacar a usuarios activos.
- **Límite de intentos de login** (rate limiting / bloqueo por fuerza bruta).
- **Revocación**: hoy un JWT robado sigue siendo válido hasta que expira; cerrar sesión solo borra la cookie de ese navegador.
