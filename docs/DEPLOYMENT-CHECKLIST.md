# Deployment Checklist

## Before deploying
- [ ] Tests pass.
- [ ] No secrets are committed.
- [ ] `JWT_SECRET` is long and random.
- [ ] Production database uses TLS.
- [ ] Redis URL uses TLS when the provider requires it.
- [ ] RabbitMQ cloud URL uses the credentials supplied by the provider.
- [ ] `DEEPSEEK_API_KEY` and `STRIPE_SECRET_KEY` exist only in backend environment variables.
- [ ] `AUTH_COOKIE_SECURE` is not set to `false` in production.
- [ ] Frontend `API_URL` is not set (stays `/api`) and the `vercel.json` rewrite points to the real backend.
- [ ] `CORS_ALLOWED_ORIGINS` contains only the real frontend URL(s).
- [ ] `/actuator/health` returns UP.
- [ ] Swagger is reviewed; consider disabling it for a real production product.

## After deploying
- [ ] Register/login works, and reloading the page keeps the session (`GET /api/auth/me` returns 200).
- [ ] The `sb_token` cookie is `HttpOnly`, `Secure` and `SameSite=Strict` (browser DevTools → Application → Cookies).
- [ ] Saving data works (no `403` for a missing CSRF token).
- [ ] Public catalog works twice (second request should be cache-eligible).
- [ ] Authenticated AI endpoint works.
- [ ] Create booking works.
- [ ] RabbitMQ queue is consumed.
- [ ] Logs contain no passwords/tokens/API keys.
- [ ] Metrics are visible locally with the observability profile.
