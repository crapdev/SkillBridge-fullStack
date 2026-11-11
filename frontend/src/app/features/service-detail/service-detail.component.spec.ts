import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ServiceDetailComponent } from './service-detail.component';
import { Offering } from '../../core/offering.service';
import { OfferingReviews } from '../../core/review.service';

const NO_REVIEWS: OfferingReviews = { offering: { average: null, count: 0 }, mentor: { average: null, count: 0 }, recent: [] };

/** SCRUM-175 (HU-20): el detalle muestra el temario real, la ficha y los horarios con hora de fin */
describe('ServiceDetailComponent · ficha de la mentoría', () => {
  let fixture: ComponentFixture<ServiceDetailComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const offering: Offering = {
    id: 'o-1', title: 'Kubernetes', description: 'Primeros pasos', category: 'CLOUD', price: 90000, active: true,
    syllabus: [
      { title: 'Pods', description: 'Qué son y cómo se crean' },
      { title: 'Deployments', description: 'Réplicas y actualizaciones' },
      { title: 'Services', description: 'Exponer la aplicación' }
    ],
    durationMinutes: 90, level: 'AVANZADO', workingDays: ['MONDAY', 'WEDNESDAY']
  };

  beforeEach(async () => {
    // IntersectionObserver (directiva reveal) no hace falta en las pruebas
    await TestBed.configureTestingModule({
      imports: [ServiceDetailComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'o-1' })) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ServiceDetailComponent);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function respond(slots: { id: string; offeringId: string; scheduledAt: string; reserved: boolean }[],
                   reviews: OfferingReviews = NO_REVIEWS) {
    http.expectOne('/api/offerings/o-1/reviews').flush(reviews);
    http.expectOne('/api/offerings/o-1').flush(offering);
    http.expectOne(r => r.url === '/api/offerings' && r.params.has('size')).flush({ content: [] });
    http.expectOne('/api/offerings/o-1/slots').flush(slots);
    fixture.detectChanges();
  }

  const text = () => (el.textContent ?? '').replace(/\s+/g, ' ');

  it('muestra el temario definido por el proveedor en orden', () => {
    respond([]);

    const topics = Array.from(el.querySelectorAll('.topic h3')).map(h => h.textContent?.trim());
    expect(topics).toEqual(['Pods', 'Deployments', 'Services']);
    expect(text()).toContain('Réplicas y actualizaciones');
  });

  it('muestra duración, nivel y días de la mentoría', () => {
    respond([]);

    expect(text()).toContain('1 h 30 min');
    expect(text()).toContain('Nivel avanzado');
    expect(text()).toContain('Lunes y miércoles');
  });

  it('muestra cada horario con hora de inicio y de finalización', () => {
    const start = new Date(2030, 0, 7, 9, 0);   // lunes 7 de enero de 2030, 9:00 hora local
    respond([{ id: 's-1', offeringId: 'o-1', scheduledAt: start.toISOString(), reserved: false }]);

    const range = (el.querySelector('.slot__range')?.textContent ?? '').replace(/\s+/g, ' ');
    expect(range).toContain('9:00');
    expect(range).toContain('10:30');   // 9:00 + 90 minutos
  });

  it('avisa cuando no hay horarios disponibles', () => {
    respond([]);
    expect(text()).toContain('Por ahora no hay horarios disponibles');
  });

  // ---- HU-27 (SCRUM-210): promedios y comentarios ----

  it('muestra el promedio de la mentoría y del mentor con la cantidad de calificaciones', () => {
    respond([], {
      offering: { average: 4.5, count: 2 },
      mentor: { average: 4.8, count: 10 },
      recent: []
    });

    const scores = Array.from(el.querySelectorAll('.score')).map(s => (s.textContent ?? '').replace(/\s+/g, ' '));
    expect(scores[0]).toContain('La mentoría');
    expect(scores[0]).toContain('4,5');
    expect(scores[0]).toContain('(2 calificaciones)');
    expect(scores[1]).toContain('El mentor');
    expect(scores[1]).toContain('4,8');
    expect(scores[1]).toContain('(10 calificaciones)');
    expect(el.querySelector('.hero-rating')?.textContent).toContain('4,5');
  });

  it('muestra los comentarios más recientes con el primer nombre de quien calificó', () => {
    respond([], {
      offering: { average: 5, count: 1 },
      mentor: { average: 4, count: 1 },
      recent: [{ rating: 5, comment: 'Muy clara', mentorRating: 4, mentorComment: 'Paciente', authorName: 'Ana',
                 createdAt: '2026-11-01T15:00:00Z' }]
    });

    const opinion = (el.querySelector('.opinion')?.textContent ?? '').replace(/\s+/g, ' ');
    expect(opinion).toContain('Ana');
    expect(opinion).toContain('Muy clara');
    expect(opinion).toContain('Sobre el mentor');
    expect(opinion).toContain('Paciente');
  });

  it('sin calificaciones lo indica en lugar de mostrar 0 estrellas', () => {
    respond([]);

    expect(text()).toContain('Aún sin calificaciones');
    expect(text()).toContain('Todavía no hay comentarios sobre esta mentoría');
    expect(el.querySelector('.stars')).toBeNull();
  });
});
