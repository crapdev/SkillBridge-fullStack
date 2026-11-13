export interface VerificationEmailTemplateData {
  name: string;
  verifyUrl: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

export interface EmailTemplate {
  subject: string;
  html: string;
  text: string;
}

const escapeHtml = (value: string): string =>
  value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    };
    return entities[character];
  });

export function buildVerificationEmailTemplate(
  data: VerificationEmailTemplateData,
): EmailTemplate {
  const safeName = escapeHtml(data.name);
  const safeUrl = escapeHtml(data.verifyUrl);
  const providerNotice =
    data.role === 'PROVIDER'
      ? '<p>Después de verificarlo, un administrador revisará tu perfil antes de que puedas iniciar sesión.</p>'
      : '';

  const providerText =
    data.role === 'PROVIDER'
      ? '\nDespués de verificarlo, un administrador revisará tu perfil antes de que puedas iniciar sesión.\n'
      : '';

  return {
    subject: 'Verifica tu correo en SkillBridge',
    html: `<!doctype html>
<html lang="es">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Verifica tu correo en SkillBridge</title>
  </head>
  <body style="margin:0;padding:32px 16px;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#20242a;">
    <main style="max-width:560px;margin:0 auto;padding:32px;background:#ffffff;border-radius:12px;">
      <h1 style="margin-top:0;font-size:24px;">Verifica tu correo</h1>
      <p>Hola ${safeName},</p>
      <p>Gracias por registrarte en SkillBridge. Para continuar, confirma tu dirección de correo electrónico.</p>
      <p style="margin:28px 0;">
        <a href="${safeUrl}" style="display:inline-block;padding:14px 22px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold;">Verificar mi correo</a>
      </p>
      <p>El enlace vence en 24 horas y solo sirve una vez.</p>
      ${providerNotice}
      <p>Si no creaste esta cuenta, ignora este correo.</p>
    </main>
  </body>
</html>`,
    text: `Verifica tu correo en SkillBridge

Hola ${data.name},

Gracias por registrarte en SkillBridge. Para continuar, confirma tu dirección de correo electrónico.

Verificar mi correo:
${data.verifyUrl}

El enlace vence en 24 horas y solo sirve una vez.
${providerText}
Si no creaste esta cuenta, ignora este correo.`,
  };
}
