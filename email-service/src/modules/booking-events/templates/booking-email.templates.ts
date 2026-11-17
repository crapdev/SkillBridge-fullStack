// Correos de reservas (HU #38): created, paid y cancelled para cliente y proveedor; completed solo para el proveedor.
// Marco HTML provisional hasta el layout común de Persona A (renderHtmlLayout / renderTextLayout).
import { escapeHtml, formatBogotaDateTime, formatCop } from './template-helpers';

export type BookingEmailKind = 'created' | 'paid' | 'cancelled' | 'completed';
export type BookingEmailAudience = 'customer' | 'provider';

export interface BookingEmailData {
  recipientName: string;
  counterpartName: string | null; // cliente → proveedor; proveedor → cliente
  offeringTitle: string;
  scheduledAt: Date;
  durationMinutes: number;
  price: string | null;
  bookingId: string;
  bookingsUrl: string;
}

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

interface Copy {
  subject: string;
  intro: string;
  cta: string;
}

// {title} se sustituye por el título de la mentoría y {other} por el nombre de la otra parte
const COPY: Record<BookingEmailKind, Partial<Record<BookingEmailAudience, Copy>>> = {
  created: {
    customer: {
      subject: 'Tu reserva fue creada',
      intro: 'Reservaste "{title}". Para confirmarla, completa el pago desde Mis reservas.',
      cta: 'Ir a Mis reservas',
    },
    provider: {
      subject: 'Tienes una reserva nueva',
      intro: '{other} reservó tu mentoría "{title}". Te avisaremos cuando la pague.',
      cta: 'Ver mis reservas',
    },
  },
  paid: {
    customer: {
      subject: 'Pago confirmado',
      intro: 'Recibimos el pago de "{title}". Tu reserva está confirmada.',
      cta: 'Ir a Mis reservas',
    },
    provider: {
      subject: 'Reserva pagada',
      intro: '{other} pagó la reserva de "{title}". La sesión está confirmada.',
      cta: 'Ver mis reservas',
    },
  },
  cancelled: {
    customer: {
      subject: 'Reserva cancelada',
      intro: 'Tu reserva de "{title}" fue cancelada.',
      cta: 'Ir a Mis reservas',
    },
    provider: {
      subject: 'Reserva cancelada',
      intro: 'La reserva de {other} para "{title}" fue cancelada.',
      cta: 'Ver mis reservas',
    },
  },
  completed: {
    provider: {
      subject: 'Sesión completada',
      intro: 'La sesión de "{title}" con {other} quedó marcada como completada.',
      cta: 'Ver mis reservas',
    },
  },
};

export function hasBookingTemplate(kind: BookingEmailKind, audience: BookingEmailAudience): boolean {
  return COPY[kind][audience] !== undefined;
}

export function renderBookingEmail(
  kind: BookingEmailKind,
  audience: BookingEmailAudience,
  data: BookingEmailData,
): RenderedEmail {
  const copy = COPY[kind][audience];
  if (!copy) throw new Error(`No hay plantilla ${kind}/${audience}`);

  const other = data.counterpartName ?? (audience === 'provider' ? 'un cliente' : 'el mentor');
  const fill = (template: string, esc: (v: string) => string) =>
    template.replace('{title}', esc(data.offeringTitle)).replace('{other}', esc(other));

  const when = formatBogotaDateTime(data.scheduledAt);
  const details: Array<[string, string]> = [
    ['Mentoría', data.offeringTitle],
    ['Fecha', `${when} (hora de Colombia)`],
    ['Duración', `${data.durationMinutes} minutos`],
    ...(data.price !== null ? [['Precio', formatCop(data.price)] as [string, string]] : []),
    ['Reserva', data.bookingId],
  ];

  const subject = `${copy.subject} · SkillBridge`;
  const html = `<!doctype html>
<html lang="es">
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,sans-serif;color:#1f2933">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">
    <h1 style="font-size:20px;margin:0 0 16px">${escapeHtml(copy.subject)}</h1>
    <p>Hola, ${escapeHtml(data.recipientName)}</p>
    <p>${fill(copy.intro, escapeHtml)}</p>
    <table style="width:100%;border-collapse:collapse;margin:16px 0">
      ${details
        .map(
          ([label, value]) =>
            `<tr><td style="padding:6px 0;color:#6b7280;width:110px">${label}</td><td style="padding:6px 0">${escapeHtml(value)}</td></tr>`,
        )
        .join('\n      ')}
    </table>
    <p style="text-align:center;margin:28px 0">
      <a href="${escapeHtml(data.bookingsUrl)}" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:bold">${copy.cta}</a>
    </p>
    <p style="font-size:12px;color:#6b7280;margin-top:32px">Recibes este correo porque tienes una cuenta en SkillBridge.</p>
  </div>
</body>
</html>`;

  const text = [
    copy.subject,
    '',
    `Hola, ${data.recipientName}`,
    '',
    fill(copy.intro, (v) => v),
    '',
    ...details.map(([label, value]) => `${label}: ${value}`),
    '',
    `${copy.cta}: ${data.bookingsUrl}`,
  ].join('\n');

  return { subject, html, text };
}
