import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MyBookingsComponent } from './my-bookings.component';
import { Booking } from '../../core/booking.service';
import { Review } from '../../core/review.service';

/** SCRUM-209 (HU-27): acción Calificar en "Mis reservas" */
describe('MyBookingsComponent · calificar', () => {
  let fixture: ComponentFixture<MyBookingsComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const past = new Date(Date.now() - 3 * 24 * 3_600_000).toISOString();
  const future = new Date(Date.now() + 5 * 24 * 3_600_000).toISOString();
  const booking = (id: string, status: Booking['status'], scheduledAt = past): Booking =>
    ({ id, offeringId: 'o-1', customerId: 'c-1', scheduledAt, status, price: 85000 });
  const review = (bookingId: string): Review =>
    ({ id: 'r-' + bookingId, bookingId, rating: 4, comment: null, mentorRating: 5, mentorComment: null, createdAt: past });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyBookingsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(MyBookingsComponent);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function respond(bookings: Booking[], reviews: Review[] | 'error') {
    http.expectOne('/api/bookings/me').flush(bookings);
    http.expectOne(r => r.url === '/api/offerings').flush({ content: [] });
    const req = http.expectOne('/api/bookings/me/reviews');
    if (reviews === 'error') req.flush(null, { status: 500, statusText: 'Error' });
    else req.flush(reviews);
    fixture.detectChanges();
    // Se muestran todas las reservas para revisar cada acción
    (Array.from(el.querySelectorAll('.tab')).find(t => t.textContent?.includes('Todas')) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  const rateButtons = () => el.querySelectorAll('.rate');

  it('ofrece calificar solo las reservas completadas que aún no se califican', () => {
    respond([
      booking('done', 'COMPLETED'),
      booking('rated', 'COMPLETED'),
      booking('confirmed', 'CONFIRMED', future),
      booking('cancelled', 'CANCELLED')
    ], [review('rated')]);

    expect(rateButtons().length).toBe(1);
    expect(el.querySelectorAll('.rated').length).toBe(1);
  });

  it('abre el formulario y, al guardar, la reserva queda como calificada', () => {
    respond([booking('done', 'COMPLETED')], []);

    (rateButtons()[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('app-review-form')).not.toBeNull();

    fixture.componentInstance.onReviewSaved(review('done'));
    fixture.detectChanges();

    expect(el.querySelector('app-review-form')).toBeNull();
    expect(rateButtons().length).toBe(0);
    expect(el.querySelector('.rated')?.textContent).toContain('Calificada');
  });

  it('si no se pudieron consultar sus calificaciones no ofrece calificar', () => {
    respond([booking('done', 'COMPLETED')], 'error');

    expect(rateButtons().length).toBe(0);
  });
});
