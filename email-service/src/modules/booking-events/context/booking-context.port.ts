/**
 * Datos que necesitan los correos de una reserva. El evento de Java solo trae ids, así que se piden aparte.
 * Contrato PROPUESTO al backend Java (aún no implementado allí): ver README, sección "Contexto de la reserva".
 */
export interface BookingEmailContext {
  bookingId: string;
  scheduledAt: Date;
  durationMinutes: number;
  offering: { title: string };
  customer: BookingParticipant;
  provider: BookingParticipant;
}

export interface BookingParticipant {
  name: string;
  email: string;
}

export abstract class BookingContextPort {
  /** Lanza BookingEmailError (reintentable o permanente) si no se puede obtener. */
  abstract getEmailContext(bookingId: string): Promise<BookingEmailContext>;
}
