import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { apiBase } from './api';

/** HU-27: promedio y cantidad de calificaciones. Sin calificaciones `average` es null. */
export interface RatingSummary {
  average: number | null;
  count: number;
}

/** Promedio de una mentoría para el catálogo (GET /api/offerings/ratings) */
export interface OfferingRating extends RatingSummary {
  offeringId: string;
}

/** Calificación que deja el cliente: la mentoría y el mentor, de 1 a 5 estrellas, con comentario opcional */
export interface ReviewInput {
  rating: number;
  comment: string | null;
  mentorRating: number;
  mentorComment: string | null;
}

export interface Review extends ReviewInput {
  id: string;
  bookingId: string;
  createdAt: string;
}

/** Calificación reciente que se muestra en el detalle (del cliente solo va su primer nombre) */
export interface PublicReview {
  rating: number;
  comment: string | null;
  mentorRating: number;
  mentorComment: string | null;
  authorName: string;
  createdAt: string;
}

/** Detalle de una mentoría: su promedio, el del mentor y los comentarios más recientes */
export interface OfferingReviews {
  offering: RatingSummary;
  mentor: RatingSummary;
  recent: PublicReview[];
}

/** El backend acepta como máximo 100 mentorías por consulta */
const MAX_IDS = 100;

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private http = inject(HttpClient);

  /** Solo el cliente dueño de una reserva completada puede calificarla, una sola vez */
  rate(bookingId: string, input: ReviewInput): Observable<Review> {
    return this.http.post<Review>(`${apiBase()}/bookings/${bookingId}/review`, input);
  }

  /** Calificaciones del cliente de la sesión: para no ofrecer calificar dos veces */
  myReviews(): Observable<Review[]> {
    return this.http.get<Review[]>(`${apiBase()}/bookings/me/reviews`);
  }

  /** Promedios de varias mentorías en una sola petición (catálogo e inicio) */
  ratings(offeringIds: string[]): Observable<OfferingRating[]> {
    const ids = [...new Set(offeringIds)].slice(0, MAX_IDS);
    if (!ids.length) return of([]);
    const params = new HttpParams().set('ids', ids.join(','));
    return this.http.get<OfferingRating[]>(`${apiBase()}/offerings/ratings`, { params });
  }

  /** Detalle público de las calificaciones de una mentoría */
  offeringReviews(offeringId: string): Observable<OfferingReviews> {
    return this.http.get<OfferingReviews>(`${apiBase()}/offerings/${offeringId}/reviews`);
  }
}

/** "4,5" con coma decimal, como el resto de cifras en español */
export function formatAverage(average: number | null): string {
  return average === null ? '–' : average.toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** "1 calificación" / "12 calificaciones" */
export function ratingCountText(count: number): string {
  return `${count} ${count === 1 ? 'calificación' : 'calificaciones'}`;
}
