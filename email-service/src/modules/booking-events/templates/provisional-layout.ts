/**
 * PROVISIONAL — pertenece a Persona A. El layout común de SkillBridge y los helpers de plantillas todavía no
 * existen en el repositorio. Este archivo es el único punto donde las plantillas de reservas dependen de ellos:
 * cuando Persona A los publique, se sustituye este archivo por llamadas a su layout y se borra.
 * No es una copia del layout de Persona A, sino el mínimo para que los correos sean legibles y seguros.
 */

export interface LayoutContent {
  heading: string;
  /** Párrafos ya en texto plano; se escapan aquí. */
  paragraphs: string[];
  cta: { label: string; url: string };
}

/** Escapa texto de usuario (nombres, títulos) antes de insertarlo en HTML. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderHtml(content: LayoutContent): string {
  const paragraphs = content.paragraphs.map((p) => `<p style="margin:0 0 16px">${escapeHtml(p)}</p>`).join('');
  return (
    '<!doctype html><html lang="es"><body style="margin:0;padding:24px;background:#f5f5f7;font-family:Arial,sans-serif;color:#1f2937">' +
    '<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">' +
    '<p style="margin:0 0 24px;font-weight:bold;color:#4f46e5">SkillBridge</p>' +
    `<h1 style="margin:0 0 16px;font-size:20px">${escapeHtml(content.heading)}</h1>` +
    paragraphs +
    `<p style="margin:24px 0 0"><a href="${escapeHtml(content.cta.url)}" style="background:#4f46e5;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none">${escapeHtml(content.cta.label)}</a></p>` +
    '</div></body></html>'
  );
}

export function renderText(content: LayoutContent): string {
  return [content.heading, '', ...content.paragraphs.flatMap((p) => [p, '']), `${content.cta.label}: ${content.cta.url}`, '', '— SkillBridge'].join('\n');
}
