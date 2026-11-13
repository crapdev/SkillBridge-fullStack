# Persona B – HU #39: email verification support

This source package implements Persona B's work from the 13-Nov handoff:
verification email template + mailer, resend endpoint, status endpoint and Jest tests.

## Runtime requirements

- Node.js 22 and npm 10.9.9 (team handoff).
- PostgreSQL database already migrated by the Java backend with Flyway V21.
- `DATABASE_URL` pointing to that same database. Do not use a separate empty database.
- The scaffold's `EmailSenderPort` and `EmailModule`.

The implementation uses TypeORM with `synchronize: false` and parameterized SQL against
`email.pending_registrations` and `email.verification_resend_requests`. It does not create
or migrate these tables; Flyway V21 remains the source of truth.

## Install and configure

From `email-service/`:

```bash
cp .env.example .env
# Edit DATABASE_URL to point to the shared SkillBridge PostgreSQL database.
npm install
npm run start:dev
```

The supplied scaffold resolves `EmailSenderPort` to `FakeEmailSenderAdapter`. When Persona A
adds the real Mailpit/Brevo adapter, this feature uses it without changing the mailer.

## Endpoints

### POST `/api/verification/resend`

Body: `{ "email": "ada@example.com" }`

- Normalizes email.
- Counts all requests, including unknown addresses, in a rolling 15-minute window.
- First three requests are accepted; the fourth and subsequent requests within the window return 429.
- For a pending registration, rotates the token hash and expiry before attempting email delivery.
- Returns the same 202 message for existing and unknown addresses.
- If delivery fails, it logs a generic message without PII and still returns 202.

### POST `/api/verification/status`

Body: `{ "email": "ada@example.com", "password": "password" }`

Returns `{ "pending": true }` only when a pending registration exists and bcrypt verifies the password.
Unknown emails use a dummy bcrypt hash comparison.

## Security

- Tokens are random 32-byte values; only SHA-256 hashes are stored.
- Token values and full email addresses are never logged.
- HTML user-provided names are escaped.
- SQL queries are parameterized.
- PostgreSQL advisory transaction locks serialize resend attempts per normalized email.
- TypeORM synchronization is disabled.

## Tests

```bash
npm test -- --runInBand
npm run build
```

Jest tests cover template rendering/escaping, provider copy, mailer URL encoding, resend behavior,
rate limiting, token rotation, delivery failure behavior, and password status checks.

## Integration ownership

The minimal `verification-mailer.port.ts` and `token.util.ts` contracts are included because
the supplied ZIP did not contain Kerin's branch files. When merging `feature/email-verification`,
compare these files with Kerin's canonical versions and keep his versions if already present.

The template uses a safe minimal layout because Persona A's shared template layout/helper were
absent from the supplied ZIP. Once Persona A merges those files, adapt the template to reuse
the shared layout and escaping helper without changing the email contract or text.

The frontend contract is unchanged. Do not change endpoint paths, payload names, status codes,
or response messages without agreement from Axel and Kerin.
