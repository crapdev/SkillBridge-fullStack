// Correo con el enlace para verificar la cuenta (HU #39).
import { RenderedEmail, renderHtmlLayout, renderTextLayout } from '../../email/templates/email-layout';

export type { RenderedEmail };

export interface VerificationEmailData {
  name: string;
  verifyUrl: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

const SUBJECT = 'Verifica tu correo en SkillBridge';
const INTRO = 'Gracias por registrarte en SkillBridge. Confirma tu correo para activar tu cuenta.';
const EXPIRY_NOTICE = 'El enlace vence en 24 horas y solo sirve una vez.';
const PROVIDER_NOTICE =
  'Después de verificarlo, un administrador revisará tu perfil antes de que puedas iniciar sesión.';
const FOOTER = 'Si no creaste esta cuenta, ignora este correo.';

export function renderVerificationEmail({ name, verifyUrl, role }: VerificationEmailData): RenderedEmail {
  const notices = [EXPIRY_NOTICE, ...(role === 'PROVIDER' ? [PROVIDER_NOTICE] : [])];
  const button = { label: 'Verificar mi correo', url: verifyUrl };
  const heading = `Hola, ${name}`; // el layout lo escapa: el nombre lo escribe el usuario

  const html = renderHtmlLayout({
    heading,
    bodyHtml: `<p>${INTRO}</p>`,
    button,
    afterButtonHtml: notices.map((n) => `<p>${n}</p>`).join('\n    '),
    footer: FOOTER,
  });

  const text = renderTextLayout({
    lines: [heading, '', INTRO, '', ...notices],
    button,
    footer: FOOTER,
  });

  return { subject: SUBJECT, html, text };
}
