import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { apiBase } from './api';

export type NotificationType = 'BOOKING_CREATED' | 'BOOKING_CANCELLED' | 'BOOKING_PAID';

export interface AppNotification {
  id: string;
  bookingId: string;
  type: NotificationType;
  message: string;
  createdAt: string; // Instant serializado
  read: boolean;
}

/** Notificaciones del proveedor sobre las reservas de sus mentorías (HU-11). El backend solo devuelve las propias. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private readonly base = `${apiBase()}/notifications/me`;

  list(limit = 10): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.base, { params: { limit } });
  }

  unreadCount(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`).pipe(map(r => r.count));
  }

  markAllRead(): Observable<void> {
    return this.http.patch<void>(`${this.base}/read-all`, {});
  }
}
