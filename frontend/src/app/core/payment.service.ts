import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { loadStripe, Stripe } from '@stripe/stripe-js';
import { apiBase } from './api';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);
  private stripe?: Promise<Stripe | null>;

  /** Clave publicable configurada en env.js; vacía si el entorno no tiene Stripe. */
  publishableKey(): string {
    return window.__env?.STRIPE_PUBLISHABLE_KEY || '';
  }

  isTestMode(): boolean {
    return this.publishableKey().startsWith('pk_test_');
  }

  /** Carga Stripe.js una sola vez (se descarga de js.stripe.com, como exige Stripe). */
  stripeClient(): Promise<Stripe | null> {
    this.stripe ??= loadStripe(this.publishableKey());
    return this.stripe;
  }

  /**
   * Pide al backend que verifique el pago directamente en Stripe y actualice la reserva.
   * No depende de que el webhook llegue (en local normalmente no llega).
   */
  sync(paymentIntentId: string): Observable<{ status: 'PENDING' | 'PAID' | 'FAILED' }> {
    return this.http.post<{ status: 'PENDING' | 'PAID' | 'FAILED' }>(`${apiBase()}/payments/${paymentIntentId}/sync`, {});
  }

  /** Pide al backend que prepare el cobro de una reserva propia. */
  createIntent(bookingId: string): Observable<{ clientSecret: string }> {
    return this.http.post<{ clientSecret: string }>(`${apiBase()}/payments/intent`, { bookingId });
  }
}
