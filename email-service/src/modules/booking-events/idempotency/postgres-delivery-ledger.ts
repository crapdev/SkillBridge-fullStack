import { DataSource } from 'typeorm';
import { ClaimResult, DeliveryKey, DeliveryLedgerPort } from './delivery-ledger.port';

const TABLE = 'email.booking_email_deliveries';

/**
 * Registro de entregas en la tabla que crea Flyway (V22). SQL parametrizado y sin synchronize: el esquema
 * es del backend Java. La reserva del envío (locked_until) evita que dos consumidores manden el mismo correo a la
 * vez y, si un proceso se cae a mitad, permite que otro intento lo retome cuando vence.
 */
export class PostgresDeliveryLedger implements DeliveryLedgerPort {
  constructor(
    private readonly dataSource: DataSource,
    private readonly leaseSeconds: number
  ) {}

  async registerPending(keys: DeliveryKey[]): Promise<void> {
    for (const key of keys) {
      await this.dataSource.query(
        `INSERT INTO ${TABLE} (booking_id, event_type, recipient_role, status)
         VALUES ($1, $2, $3, 'PENDING')
         ON CONFLICT (booking_id, event_type, recipient_role) DO NOTHING`,
        params(key)
      );
    }
  }

  async claim(key: DeliveryKey): Promise<ClaimResult> {
    // Atómico: PostgreSQL serializa el conflicto sobre la fila y solo un intento obtiene RETURNING.
    const claimed: Array<{ attempts: number }> = await this.dataSource.query(
      `INSERT INTO ${TABLE} AS d (booking_id, event_type, recipient_role, status, attempts, locked_until)
       VALUES ($1, $2, $3, 'PROCESSING', 1, NOW() + make_interval(secs => $4))
       ON CONFLICT (booking_id, event_type, recipient_role) DO UPDATE
         SET status = 'PROCESSING', attempts = d.attempts + 1,
             locked_until = NOW() + make_interval(secs => $4), updated_at = NOW()
         WHERE d.status IN ('PENDING', 'FAILED')
            OR (d.status = 'PROCESSING' AND d.locked_until < NOW())
       RETURNING d.attempts`,
      [...params(key), this.leaseSeconds]
    );
    if (claimed.length > 0) {
      return { outcome: 'claimed', attempts: Number(claimed[0].attempts) };
    }
    const current: Array<{ status: string }> = await this.dataSource.query(
      `SELECT status FROM ${TABLE} WHERE booking_id = $1 AND event_type = $2 AND recipient_role = $3`,
      params(key)
    );
    return current[0]?.status === 'SENT' ? { outcome: 'already_sent' } : { outcome: 'in_progress' };
  }

  async markSent(key: DeliveryKey, providerMessageId?: string): Promise<void> {
    await this.dataSource.query(
      `UPDATE ${TABLE}
       SET status = 'SENT', sent_at = NOW(), updated_at = NOW(), locked_until = NULL, last_error = NULL,
           provider_message_id = $4
       WHERE booking_id = $1 AND event_type = $2 AND recipient_role = $3`,
      [...params(key), providerMessageId ?? null]
    );
  }

  async markFailed(key: DeliveryKey, reason: string): Promise<void> {
    // Solo si sigue en curso: nunca se degrada un SENT a FAILED
    await this.dataSource.query(
      `UPDATE ${TABLE}
       SET status = 'FAILED', updated_at = NOW(), locked_until = NULL, last_error = $4
       WHERE booking_id = $1 AND event_type = $2 AND recipient_role = $3 AND status = 'PROCESSING'`,
      [...params(key), reason.slice(0, 120)]
    );
  }
}

function params(key: DeliveryKey): [string, string, string] {
  return [key.bookingId, key.event, key.role.toUpperCase()];
}
