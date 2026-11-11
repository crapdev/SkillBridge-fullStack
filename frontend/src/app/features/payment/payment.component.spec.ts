import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PaymentComponent } from './payment.component';
import { Booking, BookingService } from '../../core/booking.service';
import { Offering, OfferingService } from '../../core/offering.service';
import { PaymentService } from '../../core/payment.service';
import { AlertService } from '../../shared/alert.service';

/**
 * BUG-09: si el pago de la misma reserva se abrió en otra pestaña, el cobro de este formulario se cancela (o ya se
 * pagó allí). Stripe rechaza el pago sin cobrar y la pantalla explica qué pasó en lugar de un error genérico.
 */
describe('PaymentComponent · formulario de pago que quedó sin efecto', () => {
  let fixture: ComponentFixture<PaymentComponent>;
  let el: HTMLElement;
  let confirmPayment: jasmine.Spy;
  let createIntent: jasmine.Spy;
  let sync: jasmine.Spy;

  const booking: Booking = {
    id: 'b-1', offeringId: 'o-1', customerId: 'c-1', scheduledAt: '2030-01-07T15:00:00Z', status: 'CREATED',
    price: 100000
  };
  const offering: Offering = {
    // BUG-10: el proveedor subió el precio después de que el cliente reservó a 100.000
    id: 'o-1', title: 'Kubernetes', description: 'Primeros pasos', category: 'CLOUD', price: 120000, active: true,
    syllabus: [], durationMinutes: 60, level: 'BASICO', workingDays: ['MONDAY']
  };

  beforeEach(async () => {
    confirmPayment = jasmine.createSpy('confirmPayment');
    createIntent = jasmine.createSpy('createIntent').and.returnValue(of({ clientSecret: 'pi_2_secret' }));
    sync = jasmine.createSpy('sync').and.returnValue(of({ status: 'PAID' }));
    const paymentElement = { on: () => undefined, mount: () => undefined, destroy: () => undefined };
    const stripe = { elements: () => ({ create: () => paymentElement }), confirmPayment };

    await TestBed.configureTestingModule({
      imports: [PaymentComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'b-1' }), queryParamMap: convertToParamMap({}) } }
        },
        { provide: BookingService, useValue: { getMyBookings: () => of([booking]) } },
        { provide: OfferingService, useValue: { list: () => of([offering]) } },
        {
          provide: PaymentService,
          useValue: {
            publishableKey: () => 'pk_test_123', isTestMode: () => true,
            stripeClient: () => Promise.resolve(stripe), createIntent, sync
          }
        },
        { provide: AlertService, useValue: jasmine.createSpyObj('AlertService', ['success', 'error', 'info']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentComponent);
    el = fixture.nativeElement;
    fixture.autoDetectChanges(true);
    await fixture.whenStable();
  });

  const text = () => (el.textContent ?? '').replace(/\s+/g, ' ');

  async function payAndGet(error: object) {
    confirmPayment.and.resolveTo({ error });
    await fixture.componentInstance.pay();
    await fixture.whenStable();
  }

  it('explica que el formulario quedó sin efecto y permite cargar el pago de nuevo', async () => {
    expect(fixture.componentInstance.state()).toBe('form');

    await payAndGet({
      type: 'invalid_request_error', code: 'payment_intent_unexpected_state',
      payment_intent: { id: 'pi_1', status: 'canceled' }
    });

    expect(text()).toContain('Este formulario de pago ya no está vigente');
    expect(text()).toContain('No se te hizo ningún cobro');
    expect(text()).not.toContain('Ocurrió un error inesperado');

    const reload = Array.from(el.querySelectorAll('button')).find(b => b.textContent?.includes('Cargar el pago de nuevo'));
    expect(reload).withContext('botón para recargar el pago').toBeTruthy();
    reload!.click();
    await fixture.whenStable();

    // Se le pide al backend el cobro vigente de la reserva y se vuelve a mostrar el formulario
    expect(createIntent).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.state()).toBe('form');
  });

  it('si la reserva ya se pagó en otra pestaña, lo sincroniza y lo informa sin cobrar otra vez', async () => {
    await payAndGet({
      type: 'invalid_request_error', code: 'payment_intent_unexpected_state',
      payment_intent: { id: 'pi_1', status: 'succeeded' }
    });

    expect(sync).toHaveBeenCalledWith('pi_1');
    expect(text()).toContain('Esta reserva ya está pagada');
    expect(text()).toContain('No se te cobró otra vez');
  });

  it('muestra el precio guardado en la reserva, no el precio actual de la mentoría (BUG-10)', () => {
    const payButton = el.querySelector('.btn-pay')?.textContent ?? '';
    expect(payButton).toContain('100.000');
    expect(text()).not.toContain('120.000');
  });

  it('un rechazo de tarjeta sigue mostrando el mensaje de Stripe en el formulario', async () => {
    await payAndGet({ type: 'card_error', code: 'card_declined', message: 'Tu tarjeta fue rechazada.' });

    expect(fixture.componentInstance.state()).toBe('form');
    expect(el.querySelector('.form-error')?.textContent).toContain('Tu tarjeta fue rechazada.');
  });
});
