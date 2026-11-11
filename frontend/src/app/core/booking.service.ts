import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export type BookingStatus = 'CREATED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface Booking {
  id: string;          // UUID del backend
  offeringId: string;  // UUID del backend
  customerId: string;  // UUID del backend
  scheduledAt: string; // Instant serializado a string
  status: BookingStatus;
  price: number;       // BUG-10: precio con el que se hizo la reserva (es lo que se cobra)
}

@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private http = inject(HttpClient);

  getMyBookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${apiBase()}/bookings/me`);
  }

  cancelBooking(id: string): Observable<Booking> {
    return this.http.patch<Booking>(`${apiBase()}/bookings/${id}/cancel`, {});
  }
}