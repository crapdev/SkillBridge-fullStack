import { Injectable } from '@nestjs/common';
import { DataSource } from 'typeorm';

// Idempotencia (HU #38): un registro por (reserva, evento, destinatario) en email.email_log (migración V22).
@Injectable()
export class EmailLogRepository {
  constructor(private readonly dataSource: DataSource) {}

  /**
   * "Reclama" el envío en una sola sentencia. Devuelve el id del registro, o null si ya estaba SENT
   * (entonces no hay que enviar nada: es un evento repetido).
   */
  async claim(bookingId: string, routingKey: string, recipientId: string): Promise<string | null> {
    const rows: Array<{ id: string }> = await this.dataSource.query(
      `INSERT INTO email.email_log (booking_id, routing_key, recipient_id, status)
       VALUES ($1, $2, $3, 'SENDING')
       ON CONFLICT (booking_id, routing_key, recipient_id) DO UPDATE
          SET status = 'SENDING', attempts = email.email_log.attempts + 1, updated_at = NOW()
          WHERE email.email_log.status <> 'SENT'
       RETURNING id`,
      [bookingId, routingKey, recipientId],
    );
    return rows[0]?.id ?? null;
  }

  async markSent(id: string): Promise<void> {
    await this.dataSource.query(
      `UPDATE email.email_log SET status = 'SENT', last_error = NULL, updated_at = NOW() WHERE id = $1`,
      [id],
    );
  }

  // Guarda solo el mensaje del error, recortado: no debe contener el cuerpo del correo ni direcciones.
  async markFailed(id: string, error: string): Promise<void> {
    await this.dataSource.query(
      `UPDATE email.email_log SET status = 'FAILED', last_error = $2, updated_at = NOW() WHERE id = $1`,
      [id, error.slice(0, 300)],
    );
  }
}
