import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReviewFormComponent } from './review-form.component';
import { Review } from '../../core/review.service';

/** SCRUM-209 (HU-27): formulario para calificar una reserva completada */
describe('ReviewFormComponent', () => {
  let fixture: ComponentFixture<ReviewFormComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReviewFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewFormComponent);
    fixture.componentRef.setInput('bookingId', 'b-1');
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Pulsa la estrella n (1 a 5) del grupo 0 (mentoría) o 1 (mentor) */
  const star = (group: number, n: number) => {
    const groups = el.querySelectorAll('.rf__stars');
    (groups[group].querySelectorAll('.rf__star')[n - 1] as HTMLButtonElement).click();
    fixture.detectChanges();
  };
  const type = (index: number, value: string) => {
    const area = el.querySelectorAll('textarea')[index] as HTMLTextAreaElement;
    area.value = value;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const submit = () => {
    (el.querySelector('.rf__send') as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  it('pide calificar la mentoría y al mentor por separado', () => {
    const legends = Array.from(el.querySelectorAll('legend')).map(l => l.textContent?.trim());
    expect(legends).toEqual(['La mentoría', 'El mentor']);
    expect(el.querySelectorAll('.rf__star').length).toBe(10);
  });

  it('no envía sin estrellas en las dos partes', () => {
    star(0, 4);
    submit();

    http.expectNone('/api/bookings/b-1/review');
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Califica la mentoría y al mentor');
  });

  it('envía estrellas y comentarios (vacío = sin comentario) y avisa al guardar', () => {
    let saved: Review | undefined;
    fixture.componentInstance.saved.subscribe(r => saved = r);
    star(0, 5);
    star(1, 3);
    type(0, '  Muy clara  ');
    type(1, '   ');
    submit();

    const req = http.expectOne('/api/bookings/b-1/review');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ rating: 5, comment: 'Muy clara', mentorRating: 3, mentorComment: null });
    const review: Review = { id: 'r-1', bookingId: 'b-1', rating: 5, comment: 'Muy clara', mentorRating: 3,
                             mentorComment: null, createdAt: '2026-11-11T15:00:00Z' };
    req.flush(review);

    expect(saved).toEqual(review);
  });

  it('marca las estrellas elegidas como seleccionadas', () => {
    star(0, 3);
    const on = el.querySelectorAll('.rf__stars')[0].querySelectorAll('.rf__star.on').length;
    expect(on).toBe(3);
    expect(el.querySelectorAll('.rf__stars')[0].querySelector('[aria-checked=true]')?.getAttribute('aria-label'))
      .toContain('3 estrellas');
  });

  it('si la reserva ya tenía calificación (409) avisa al componente padre', () => {
    let already = false;
    fixture.componentInstance.alreadyRated.subscribe(() => already = true);
    star(0, 4);
    star(1, 4);
    submit();

    http.expectOne('/api/bookings/b-1/review').flush({ detail: 'Ya calificaste esta mentoría' },
      { status: 409, statusText: 'Conflict' });
    expect(already).toBeTrue();
  });

  it('muestra el error del backend y permite reintentar', () => {
    star(0, 4);
    star(1, 4);
    submit();

    http.expectOne('/api/bookings/b-1/review').flush(
      { detail: 'Solo puedes calificar una mentoría después de completar la sesión' },
      { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('después de completar la sesión');
    expect((el.querySelector('.rf__send') as HTMLButtonElement).disabled).toBeFalse();
  });
});
