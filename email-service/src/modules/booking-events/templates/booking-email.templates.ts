import { BookingRoutingKey } from '../booking-event.types';
import { formatSessionDate } from './session-date';
import { LayoutContent, renderHtml, renderText } from './provisional-layout';

export type RecipientRole = 'customer' | 'provider';

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

export interface BookingTemplateData {
  recipientName: string;
  /** Nombre del cliente: el proveedor necesita saber quién reservó. */
  customerName: string;
  offeringTitle: string;
  scheduledAt: Date;
  durationMinutes: number;
  /** Enlace ya resuelto según el rol (Mis reservas o panel del proveedor). */
  link: string;
}

type Template = (d: BookingTemplateData, when: string) => { subject: string; content: LayoutContent };

const session = (d: BookingTemplateData, when: string) => `${d.offeringTitle}, el ${when} (${d.durationMinutes} minutos)`;

const TEMPLATES: Record<BookingRoutingKey, Record<RecipientRole, Template>> = {
  'booking.created': {
    customer: (d, when) => ({
      subject: `Reserva creada: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`Tu reserva de ${session(d, when)} quedó creada.`, 'Para confirmarla, completa el pago desde Mis reservas.'],
        cta: { label: 'Ver mis reservas', url: d.link }
      }
    }),
    provider: (d, when) => ({
      subject: `Nueva reserva: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`${d.customerName} reservó ${session(d, when)}.`, 'La reserva queda pendiente hasta que se confirme el pago.'],
        cta: { label: 'Ir a mi panel', url: d.link }
      }
    })
  },
  'booking.paid': {
    customer: (d, when) => ({
      subject: `Pago confirmado: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`Recibimos tu pago. Tu sesión de ${session(d, when)} está confirmada.`],
        cta: { label: 'Ver mis reservas', url: d.link }
      }
    }),
    provider: (d, when) => ({
      subject: `Reserva confirmada: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`${d.customerName} pagó la reserva de ${session(d, when)}. La sesión está confirmada.`],
        cta: { label: 'Ir a mi panel', url: d.link }
      }
    })
  },
  'booking.cancelled': {
    customer: (d, when) => ({
      subject: `Reserva cancelada: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`Tu reserva de ${session(d, when)} fue cancelada.`],
        cta: { label: 'Ver mis reservas', url: d.link }
      }
    }),
    provider: (d, when) => ({
      subject: `Reserva cancelada: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`${d.customerName} canceló la reserva de ${session(d, when)}.`, 'El horario vuelve a estar disponible para otras reservas.'],
        cta: { label: 'Ir a mi panel', url: d.link }
      }
    })
  },
  'booking.completed': {
    customer: (d, when) => ({
      subject: `¿Cómo te fue en ${d.offeringTitle}?`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`Tu sesión de ${session(d, when)} terminó.`, 'Cuéntanos cómo te fue: puedes calificarla desde Mis reservas.'],
        cta: { label: 'Calificar la sesión', url: d.link }
      }
    }),
    provider: (d, when) => ({
      subject: `Sesión completada: ${d.offeringTitle}`,
      content: {
        heading: `Hola, ${d.recipientName}`,
        paragraphs: [`Tu sesión de ${session(d, when)} con ${d.customerName} se marcó como completada.`],
        cta: { label: 'Ir a mi panel', url: d.link }
      }
    })
  }
};

export function renderBookingEmail(event: BookingRoutingKey, role: RecipientRole, data: BookingTemplateData): RenderedEmail {
  const { subject, content } = TEMPLATES[event][role](data, formatSessionDate(data.scheduledAt));
  return { subject, html: renderHtml(content), text: renderText(content) };
}
