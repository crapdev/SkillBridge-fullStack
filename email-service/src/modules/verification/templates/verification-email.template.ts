// Correo con el enlace para verificar la cuenta (HU #39).
// Provisional hasta que exista el layout común de src/modules/email/templates/:
// entonces se cambia el marco HTML por renderHtmlLayout/renderTextLayout y escapeHtml por el compartido.

export interface VerificationEmailData {
  name: string;
  verifyUrl: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

const SUBJECT = 'Verifica tu correo en SkillBridge';
const EXPIRY_NOTICE = 'El enlace vence en 24 horas y solo sirve una vez.';
const PROVIDER_NOTICE =
  'Después de verificarlo, un administrador revisará tu perfil antes de que puedas iniciar sesión.';
const FOOTER = 'Si no creaste esta cuenta, ignora este correo.';

// El nombre lo escribe el usuario: sin escaparlo podría inyectar HTML en el correo.
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderVerificationEmail({ name, verifyUrl, role }: VerificationEmailData): RenderedEmail {
  const isProvider = role === 'PROVIDER';
  const url = escapeHtml(verifyUrl);

  const html = `<!doctype html>
<html lang="es">
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,sans-serif;color:#1f2933">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">
    <h1 style="font-size:20px;margin:0 0 16px">Hola, ${escapeHtml(name)}</h1>
    <p>Gracias por registrarte en SkillBridge. Confirma tu correo para activar tu cuenta.</p>
    <p style="text-align:center;margin:28px 0">
      <a href="${url}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:bold">Verificar mi correo</a>
    </p>
    <p>${EXPIRY_NOTICE}</p>
    ${isProvider ? `<p>${PROVIDER_NOTICE}</p>` : ''}
    <p style="font-size:12px;color:#6b7280;margin-top:32px">${FOOTER}</p>
  </div>
</body>
</html>`;

  const text = [
    `Hola, ${name}`,
    '',
    'Gracias por registrarte en SkillBridge. Confirma tu correo para activar tu cuenta abriendo este enlace:',
    verifyUrl,
    '',
    EXPIRY_NOTICE,
    ...(isProvider ? [PROVIDER_NOTICE] : []),
    '',
    FOOTER,
  ].join('\n');

  return { subject: SUBJECT, html, text };
}
