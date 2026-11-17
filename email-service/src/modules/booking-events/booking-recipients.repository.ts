import { Injectable } from '@nestjs/common';
import { DataSource } from 'typeorm';

export interface BookingEmailContext {
  bookingId: string;
  scheduledAt: Date;
  price: string | null; // NUMERIC llega como texto desde pg
  offeringTitle: string;
  durationMinutes: number;
  customer: { id: string; name: string; email: string };
  provider: { id: string; name: string; email: string } | null; // datos antiguos pueden no tener proveedor
}

interface Row {
  id: string;
  scheduled_at: Date;
  price: string | null;
  title: string;
  duration_minutes: number;
  customer_id: string;
  customer_name: string;
  customer_email: string;
  provider_id: string | null;
  provider_name: string | null;
  provider_email: string | null;
}

// Solo lectura sobre las tablas de Java: el evento trae ids y los nombres y correos se leen aquí.
@Injectable()
export class BookingRecipientsRepository {
  constructor(private readonly dataSource: DataSource) {}

  async findContext(bookingId: string): Promise<BookingEmailContext | null> {
    const rows: Row[] = await this.dataSource.query(
      `SELECT b.id, b.scheduled_at, b.price, o.title, o.duration_minutes,
              c.id AS customer_id, c.name AS customer_name, c.email AS customer_email,
              p.id AS provider_id, p.name AS provider_name, p.email AS provider_email
       FROM bookings b
       JOIN offerings o ON o.id = b.offering_id
       JOIN app_users c ON c.id = b.customer_id
       LEFT JOIN app_users p ON p.id = o.provider_id
       WHERE b.id = $1`,
      [bookingId],
    );
    const row = rows[0];
    if (!row) return null;

    return {
      bookingId: row.id,
      scheduledAt: new Date(row.scheduled_at),
      price: row.price,
      offeringTitle: row.title,
      durationMinutes: row.duration_minutes,
      customer: { id: row.customer_id, name: row.customer_name, email: row.customer_email },
      provider: row.provider_id
        ? { id: row.provider_id, name: row.provider_name ?? '', email: row.provider_email ?? '' }
        : null,
    };
  }
}
