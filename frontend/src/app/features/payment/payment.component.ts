import { Component, ElementRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';
import type { Appearance, Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';
import { Booking, BookingService } from '../../core/booking.service';
import { Offering, OfferingService } from '../../core/offering.service';
import { PaymentService } from '../../core/payment.service';
import { AlertService } from '../../shared/alert.service';

type ViewState = 'loading' | 'notice' | 'form' | 'success';

interface Notice {
  tone: 'info' | 'error';
  title: string;
  message: string;
}

/** Colores del rediseño aplicados al formulario de Stripe */
const STRIPE_APPEARANCE: Appearance = {
  theme: 'stripe',
  variables: {
    colorPrimary: '#2563eb',
    colorText: '#0f1b3d',
    colorTextSecondary: '#475467',
    colorDanger: '#dc2626',
    colorBackground: '#ffffff',
    fontFamily: 'Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif',
    borderRadius: '8px',
    spacingUnit: '4px'
  },
  rules: {
    '.Input': { backgroundColor: '#f4f6fb', border: '1px solid transparent', boxShadow: 'none' },
    '.Input:focus': { backgroundColor: '#ffffff', borderColor: '#2563eb', boxShadow: '0 0 0 3px rgba(37, 99, 235, .15)' },
    '.Label': { fontWeight: '600' },
    '.Tab': { border: '1px solid #e4e8f0', boxShadow: 'none' },
    '.Tab--selected': { borderColor: '#2563eb', boxShadow: '0 0 0 1px #2563eb' }
  }
};

@Component({
  selector: 'app-payment',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './payment.component.html',
  styleUrls: ['./payment.component.css']
})
export class PaymentComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private bookings = inject(BookingService);
  private offerings = inject(OfferingService);
  private alerts = inject(AlertService);
  readonly payments = inject(PaymentService);

  private readonly elementHost = viewChild<ElementRef<HTMLDivElement>>('paymentElement');

  readonly state = signal<ViewState>('loading');
  readonly notice = signal<Notice | null>(null);
  readonly booking = signal<Booking | null>(null);
  readonly offering = signal<Offering | null>(null);
  readonly elementReady = signal(false);
  readonly formComplete = signal(false);
  readonly processing = signal(false);
  readonly formError = signal('');

  private bookingId = '';
  private stripe: Stripe | null = null;
  private elements?: StripeElements;
  private paymentElement?: StripePaymentElement;

  private readonly currency = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
  private readonly dateFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  private readonly timeFormat = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit' });

  async ngOnInit(): Promise<void> {
    this.bookingId = this.route.snapshot.paramMap.get('id') ?? '';

    if (!this.payments.publishableKey()) {
      return this.showNotice('error', 'Pagos no disponibles', 'Este entorno no tiene configurada la pasarela de pagos. Contacta al equipo de soporte.');
    }

    try {
      const [bookings, offerings] = await firstValueFrom(forkJoin([this.bookings.getMyBookings(), this.offerings.list()]));
      const booking = bookings.find(b => b.id === this.bookingId) ?? null;
      this.booking.set(booking);
      this.offering.set(offerings.find(o => o.id === booking?.offeringId) ?? null);

      // Al volver de un método de pago con redirección, Stripe agrega el resultado a la URL
      const returnedSecret = this.route.snapshot.queryParamMap.get('payment_intent_client_secret');
      if (returnedSecret) {
        return this.showReturnResult(returnedSecret);
      }

      if (!booking) {
        return this.showNotice('error', 'Reserva no encontrada', 'No encontramos esta reserva entre las tuyas.');
      }
      if (booking.status === 'CONFIRMED' || booking.status === 'COMPLETED') {
        return this.showNotice('info', 'Esta reserva ya está pagada', 'No tienes nada pendiente por pagar en esta reserva.');
      }
      if (booking.status === 'CANCELLED') {
        return this.showNotice('error', 'Reserva cancelada', 'Las reservas canceladas no se pueden pagar.');
      }

      await this.mountPaymentForm();
    } catch (error) {
      this.showNotice('error', 'No pudimos preparar el pago', this.problemDetail(error, 'Inténtalo de nuevo en unos minutos.'));
    }
  }

  ngOnDestroy(): void {
    this.paymentElement?.destroy();
  }

  price(): string {
    const amount = this.offering()?.price;
    return amount == null ? '' : this.currency.format(amount);
  }

  sessionDate(): string {
    const b = this.booking();
    if (!b) return '';
    const text = this.dateFormat.format(new Date(b.scheduledAt));
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  sessionTime(): string {
    const b = this.booking();
    return b ? this.timeFormat.format(new Date(b.scheduledAt)) : '';
  }

  async pay(): Promise<void> {
    if (!this.stripe || !this.elements || this.processing()) return;
    this.processing.set(true);
    this.formError.set('');

    const { error, paymentIntent } = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: { return_url: `${location.origin}/bookings/${this.bookingId}/pay` },
      // Las tarjetas se resuelven aquí mismo; solo algunos métodos necesitan redirigir
      redirect: 'if_required'
    });

    if (error) {
      this.processing.set(false);
      // Errores de tarjeta y validación traen un mensaje pensado para el usuario
      const message = error.type === 'card_error' || error.type === 'validation_error'
        ? error.message ?? 'Revisa los datos de tu tarjeta.'
        : 'Ocurrió un error inesperado al procesar el pago.';
      this.formError.set(message);
      this.alerts.error('Pago no completado', message);
      return;
    }

    if (paymentIntent && (paymentIntent.status === 'succeeded' || paymentIntent.status === 'processing')) {
      await this.syncWithBackend(paymentIntent.id);
      this.processing.set(false);
      this.state.set('success');
      this.alerts.success('Pago aprobado', 'Tu reserva quedó confirmada.');
    } else {
      this.processing.set(false);
    }
  }

  /**
   * El backend consulta el pago en Stripe y confirma la reserva. Si Stripe aún lo está procesando,
   * se reintenta unas veces; si todo falla, el webhook lo actualizará cuando llegue.
   */
  private async syncWithBackend(paymentIntentId: string): Promise<void> {
    for (let attempt = 0; attempt < 4; attempt++) {
      try {
        const { status } = await firstValueFrom(this.payments.sync(paymentIntentId));
        if (status !== 'PENDING') return;
      } catch {
        return;
      }
      await new Promise(resolve => setTimeout(resolve, 1500));
    }
  }

  private async mountPaymentForm(): Promise<void> {
    const { clientSecret } = await firstValueFrom(this.payments.createIntent(this.bookingId));
    this.stripe = await this.payments.stripeClient();
    if (!this.stripe) {
      throw new Error('Stripe no cargó');
    }

    // El contenedor del formulario existe en el DOM a partir del estado 'form'
    this.state.set('form');
    await new Promise(resolve => setTimeout(resolve));
    const host = this.elementHost()?.nativeElement;
    if (!host) return;

    this.elements = this.stripe.elements({ clientSecret, appearance: STRIPE_APPEARANCE, locale: 'es' });
    this.paymentElement = this.elements.create('payment', { layout: 'tabs' });
    this.paymentElement.on('ready', () => this.elementReady.set(true));
    this.paymentElement.on('change', event => {
      this.formComplete.set(event.complete);
      this.formError.set('');
    });
    this.paymentElement.mount(host);
  }

  private async showReturnResult(clientSecret: string): Promise<void> {
    this.stripe = await this.payments.stripeClient();
    const result = await this.stripe?.retrievePaymentIntent(clientSecret);
    const intent = result?.paymentIntent;
    if (intent && (intent.status === 'succeeded' || intent.status === 'processing')) {
      await this.syncWithBackend(intent.id);
      this.state.set('success');
    } else {
      this.showNotice('error', 'El pago no se completó', 'Puedes intentarlo de nuevo desde Mis reservas.');
    }
  }

  private showNotice(tone: Notice['tone'], title: string, message: string): void {
    this.notice.set({ tone, title, message });
    this.state.set('notice');
  }

  private problemDetail(error: unknown, fallback: string): string {
    const detail = (error as { error?: { detail?: string } })?.error?.detail;
    return detail || fallback;
  }
}
