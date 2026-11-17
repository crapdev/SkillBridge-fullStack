// Marco común de todos los correos: tarjeta blanca centrada, título, botón y pie.
// Cada plantilla solo aporta su contenido; así todos los correos se ven igual y el estilo se cambia en un sitio.
import { escapeHtml } from './template-helpers';

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

export interface EmailLayout {
  heading: string;              // texto plano; se escapa aquí
  bodyHtml: string;             // HTML ya escapado por la plantilla
  button?: { label: string; url: string };
  afterButtonHtml?: string;     // avisos debajo del botón (HTML ya escapado)
  footer: string;               // texto plano; se escapa aquí
}

export function renderHtmlLayout({ heading, bodyHtml, button, afterButtonHtml = '', footer }: EmailLayout): string {
  const buttonHtml = button
    ? `
    <p style="text-align:center;margin:28px 0">
      <a href="${escapeHtml(button.url)}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:bold">${escapeHtml(button.label)}</a>
    </p>`
    : '';

  return `<!doctype html>
<html lang="es">
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,sans-serif;color:#1f2933">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">
    <h1 style="font-size:20px;margin:0 0 16px">${escapeHtml(heading)}</h1>
    ${bodyHtml}${buttonHtml}
    ${afterButtonHtml}
    <p style="font-size:12px;color:#6b7280;margin-top:32px">${escapeHtml(footer)}</p>
  </div>
</body>
</html>`;
}

// Versión de texto plano: para clientes de correo sin HTML y para los filtros de spam
export function renderTextLayout({ lines, button, footer }: {
  lines: string[];
  button?: { label: string; url: string };
  footer: string;
}): string {
  return [...lines, ...(button ? ['', `${button.label}: ${button.url}`] : []), '', footer].join('\n');
}
