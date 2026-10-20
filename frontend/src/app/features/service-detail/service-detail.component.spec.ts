import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ServiceDetailComponent } from './service-detail.component';
import { Offering } from '../../core/offering.service';

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

  function respond(slots: { id: string; offeringId: string; scheduledAt: string; reserved: boolean }[]) {
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
});
