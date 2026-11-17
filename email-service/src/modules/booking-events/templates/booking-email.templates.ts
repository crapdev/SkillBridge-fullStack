// Correos de reservas (HU #38): created, paid y cancelled para cliente y proveedor; completed solo para el proveedor.
import { RenderedEmail, renderHtmlLayout, renderTextLayout } from '../../email/templates/email-layout';
import { escapeHtml, formatBogotaDateTime, formatCop } from '../../email/templates/template-helpers';

export type { RenderedEmail };

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
  const button = { label: copy.cta, url: data.bookingsUrl };
  const footer = 'Recibes este correo porque tienes una cuenta en SkillBridge.';

  const rows = details
    .map(
      ([label, value]) =>
        `<tr><td style="padding:6px 0;color:#6b7280;width:110px">${label}</td><td style="padding:6px 0">${escapeHtml(value)}</td></tr>`,
    )
    .join('\n      ');

  const html = renderHtmlLayout({
    heading: copy.subject,
    bodyHtml: `<p>Hola, ${escapeHtml(data.recipientName)}</p>
    <p>${fill(copy.intro, escapeHtml)}</p>
    <table style="width:100%;border-collapse:collapse;margin:16px 0">
      ${rows}
    </table>`,
    button,
    footer,
  });

  const text = renderTextLayout({
    lines: [
      copy.subject,
      '',
      `Hola, ${data.recipientName}`,
      '',
      fill(copy.intro, (v) => v),
      '',
      ...details.map(([label, value]) => `${label}: ${value}`),
    ],
    button,
    footer,
  });

  return { subject, html, text };
}
