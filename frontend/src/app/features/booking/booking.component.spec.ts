import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BookingComponent } from './booking.component';
import { AvailabilitySlot, Offering } from '../../core/offering.service';

/** BUG-08: si otro cliente gana el horario al mismo tiempo, el formulario deja de ofrecerlo */
describe('BookingComponent · horario apartado por otro cliente', () => {
  let fixture: ComponentFixture<BookingComponent>;
  let component: BookingComponent;
  let http: HttpTestingController;
  let el: HTMLElement;

  const offering: Offering = {
    id: 'o-1', title: 'Kubernetes', description: 'Primeros pasos', category: 'CLOUD', price: 90000, active: true,
    syllabus: [], durationMinutes: 60, level: 'BASICO', workingDays: ['MONDAY']
  };
  // Dos horarios del mismo día: al perder uno, el otro sigue disponible
  const nine: AvailabilitySlot = { id: 's-9', offeringId: 'o-1', scheduledAt: '2030-01-07T14:00:00Z', reserved: false };
  const ten: AvailabilitySlot = { id: 's-10', offeringId: 'o-1', scheduledAt: '2030-01-07T15:00:00Z', reserved: false };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ offering: 'o-1' }) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookingComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();

    http.expectOne(r => r.url === '/api/offerings' && r.params.has('size')).flush([offering]);
    http.expectOne('/api/offerings/o-1/slots').flush([nine, ten]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const shownSlotIds = () => component.dayTimes().map(s => s.id);

  function bookNineAndRespond(body: object, status: number, statusText: string) {
    component.selectedSlot.set(nine);
    component.book();
    const request = http.expectOne('/api/bookings');
    expect(request.request.body).toEqual({ offeringId: 'o-1', scheduledAt: nine.scheduledAt });
    request.flush(body, { status, statusText });
    fixture.detectChanges();
  }

  it('quita el horario y muestra el mensaje cuando la API responde 409', () => {
    const detail = 'Este horario acaba de ser apartado por otro usuario. Elige otro horario.';

    bookNineAndRespond({ detail }, 409, 'Conflict');

    expect(shownSlotIds()).toEqual(['s-10']);
    expect(component.selectedSlot()).toBeNull();
    expect(el.querySelector('.error')?.textContent).toContain(detail);
    expect((el.querySelector('.btn') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('deja el horario cuando el error es otro (por ejemplo 422)', () => {
    bookNineAndRespond({ detail: 'Ya tienes otra mentoría agendada en este mismo horario' }, 422, 'Unprocessable Entity');

    expect(shownSlotIds()).toEqual(['s-9', 's-10']);
    expect(component.selectedSlot()).toEqual(nine);
  });

  it('quita el horario cuando la reserva se crea', () => {
    bookNineAndRespond({ id: 'b-1' }, 200, 'OK');

    expect(shownSlotIds()).toEqual(['s-10']);
    expect(el.querySelector('.success')?.textContent).toContain('¡Reserva creada!');
  });
});
