import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ProviderSlotsComponent } from './provider-slots.component';
import { Offering } from '../../core/offering.service';

/** SCRUM-175 (HU-20): al publicar horarios solo se pueden elegir los días que ofrece la mentoría */
describe('ProviderSlotsComponent · días de la mentoría', () => {
  let fixture: ComponentFixture<ProviderSlotsComponent>;
  let component: ProviderSlotsComponent;
  let http: HttpTestingController;

  const offering: Offering = {
    id: 'o-1', title: 'Kubernetes', description: 'Pods', category: 'CLOUD', price: 90000, active: true,
    syllabus: [{ title: 'A', description: 'a' }, { title: 'B', description: 'b' }],
    durationMinutes: 90, level: 'BASICO', workingDays: ['MONDAY', 'WEDNESDAY']
  };

  const pad = (n: number) => String(n).padStart(2, '0');
  const toInput = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  /** Próxima fecha (desde pasado mañana) que cae en ese día: 0 = domingo … 6 = sábado */
  function next(jsDay: number): string {
    const d = new Date();
    d.setDate(d.getDate() + 2);
    while (d.getDay() !== jsDay) d.setDate(d.getDate() + 1);
    return toInput(d);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProviderSlotsComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'o-1' }) } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProviderSlotsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/provider/offerings').flush([offering]);
    http.expectOne('/api/provider/offerings/o-1/slots').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('arranca en un día que la mentoría ofrece', () => {
    expect(component.dayAllowed()).toBeTrue();
    expect(['MONDAY', 'WEDNESDAY']).toContain(
      ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][new Date(`${component.date()}T00:00`).getDay()]
    );
  });

  it('bloquea las horas y avisa si se elige un día que no se ofrece', () => {
    component.setDate(next(6));   // sábado
    fixture.detectChanges();

    expect(component.dayAllowed()).toBeFalse();
    const warning = fixture.nativeElement.querySelector('.day-warning')?.textContent ?? '';
    expect(warning).toContain('no se ofrece los sábados');
    expect(warning).toContain('lunes y miércoles');

    component.toggleTime('10:00');
    expect(component.selectedTimes().size).toBe(0);
    const firstHour = fixture.nativeElement.querySelector('.time') as HTMLButtonElement;
    expect(firstHour.disabled).toBeTrue();
  });

  it('el botón del aviso salta al siguiente día disponible', () => {
    component.setDate(next(4));   // jueves → el siguiente ofrecido es lunes
    component.goToNextAllowedDay();

    expect(component.dayAllowed()).toBeTrue();
    expect(new Date(`${component.date()}T00:00`).getDay()).toBe(1);
  });

  it('en un día ofrecido deja seleccionar horas y publica', () => {
    component.setDate(next(3));   // miércoles
    component.toggleTime('10:00');
    expect(component.selectedTimes().has('10:00')).toBeTrue();

    component.publish();
    const req = http.expectOne({ method: 'POST', url: '/api/provider/offerings/o-1/slots' });
    req.flush({ id: 's-1', offeringId: 'o-1', scheduledAt: new Date(`${component.date()}T10:00`).toISOString(), reserved: false });
  });

  it('muestra la duración y los días de la mentoría', () => {
    const text = (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
    expect(text).toContain('Lunes y miércoles');
    expect(text).toContain('1 h 30 min');
  });
});
