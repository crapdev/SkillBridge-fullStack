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
    component.setRepeat(0);
    component.setDate(next(3));   // miércoles
    component.toggleTime('10:00');
    expect(component.selectedTimes().has('10:00')).toBeTrue();

    component.publish();
    const req = http.expectOne({ method: 'POST', url: '/api/provider/offerings/o-1/slots/batch' });
    const at = new Date(`${component.date()}T10:00`).toISOString();
    expect(req.request.body).toEqual({ scheduledAt: [at] });
    req.flush({ created: [{ id: 's-1', offeringId: 'o-1', scheduledAt: at, reserved: false }], skipped: [] });
    expect(component.slots().length).toBe(1);
  });

  describe('repetir en los días de la mentoría', () => {
    const sorted = (list: string[]) => [...list].sort();

    it('repite las horas marcadas en cada día ofrecido durante las semanas elegidas', () => {
      component.setDate(next(1));   // lunes
      component.setRepeat(2);
      component.toggleTime('10:00');
      component.toggleTime('13:00');

      // 2 semanas desde un lunes: lunes, miércoles, lunes, miércoles × 2 horas
      expect(component.targetDates().length).toBe(4);
      expect(component.targetDates().every(d => [1, 3].includes(new Date(`${d}T00:00`).getDay()))).toBeTrue();
      expect(component.plannedCount()).toBe(8);

      component.publish();
      const req = http.expectOne({ method: 'POST', url: '/api/provider/offerings/o-1/slots/batch' });
      const expected = component.targetDates().flatMap(d => ['10:00', '13:00'].map(t => new Date(`${d}T${t}`).toISOString()));
      expect(sorted(req.request.body.scheduledAt)).toEqual(sorted(expected));
      req.flush({ created: [], skipped: [] });
    });

    it('"Solo este día" publica únicamente en el día elegido', () => {
      component.setDate(next(1));
      component.setRepeat(0);
      component.toggleTime('10:00');
      expect(component.targetDates()).toEqual([component.date()]);
      expect(component.plannedCount()).toBe(1);
    });

    it('el botón muestra cuántos horarios se publicarán en total', () => {
      component.setDate(next(1));
      component.setRepeat(1);
      component.toggleTime('10:00');
      fixture.detectChanges();
      const button = (fixture.nativeElement as HTMLElement).querySelector('.btn--block')!;
      expect(button.textContent).toContain('Publicar 2 horarios');
    });

    it('agrega los creados a la lista y avisa los omitidos con su motivo', () => {
      component.setDate(next(1));
      component.setRepeat(1);
      component.toggleTime('10:00');
      component.publish();

      const [first, second] = component.targetDates().map(d => new Date(`${d}T10:00`).toISOString());
      http.expectOne('/api/provider/offerings/o-1/slots/batch').flush({
        created: [{ id: 's-1', offeringId: 'o-1', scheduledAt: first, reserved: false }],
        skipped: [{ scheduledAt: second, reason: 'Se cruza con una sesión de «Angular» de 10:00 a 11:00' }]
      });
      fixture.detectChanges();

      expect(component.slots().length).toBe(1);
      expect(component.selectedTimes().size).toBe(0);
      expect(component.publishing()).toBeFalse();
    });
  });

  // La mentoría dura 90 minutos: una sesión a las 13:00 ocupa hasta las 14:30
  it('al marcar una hora bloquea las que se cruzarían con ella', () => {
    component.setDate(next(3));   // miércoles
    component.toggleTime('13:00');

    expect(component.overlaps('14:00')).toBeTrue();
    component.toggleTime('14:00');
    expect(component.selectedTimes().has('14:00')).toBeFalse();
    // 15:00 empieza después de las 14:30: no se cruza
    component.toggleTime('15:00');
    expect([...component.selectedTimes()].sort()).toEqual(['13:00', '15:00']);
  });

  it('una hora marcada siempre se puede desmarcar', () => {
    component.setDate(next(3));
    component.toggleTime('13:00');
    component.toggleTime('13:00');

    expect(component.selectedTimes().size).toBe(0);
    expect(component.overlaps('14:00')).toBeFalse();
  });

  it('bloquea las horas que se cruzarían con un horario ya publicado', () => {
    component.setDate(next(3));
    // Publicado a las 13:30 (fuera de la cuadrícula de 90 min): igual aparece, como publicado
    component.slots.set([{ id: 's', offeringId: 'o-1', scheduledAt: new Date(`${component.date()}T13:30`).toISOString(), reserved: false }]);
    fixture.detectChanges();

    expect(component.times()).toContain('13:30');
    expect(component.overlaps('13:00')).toBeTrue();   // 13:00–14:30 se cruza con 13:30–15:00
    expect(component.overlaps('14:30')).toBeTrue();   // 14:30 empieza antes de que termine (15:00)
    expect(component.overlaps('11:30')).toBeFalse();  // 11:30–13:00 termina antes de que empiece
    const button = [...fixture.nativeElement.querySelectorAll('.time')]
      .find((b: Element) => b.querySelector('.time__start')?.textContent?.trim() === '13:00') as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
    expect(button.title).toContain('Se cruza con otra sesión');
  });

  // ---- La cuadrícula se adapta a la duración de la mentoría ----

  it('con sesiones de 90 minutos propone horas que encajan una detrás de otra y terminan antes de las 21:00', () => {
    expect(component.times()).toEqual(['07:00', '08:30', '10:00', '11:30', '13:00', '14:30', '16:00', '17:30', '19:00']);
    expect(component.endOf('13:00')).toBe('14:30');
  });

  it('con sesiones de 30 minutos propone cada media hora, agrupadas en mañana, tarde y noche', () => {
    component.offering.set({ ...offering, durationMinutes: 30 });

    expect(component.times().length).toBe(28);   // 07:00 … 20:30
    expect(component.times()).toContain('13:30');
    expect(component.periods().map(p => p.label)).toEqual(['Mañana', 'Tarde', 'Noche']);
    expect(component.periods()[0].times.at(-1)).toBe('11:30');
  });

  it('cada botón muestra el rango de la sesión', () => {
    const first = fixture.nativeElement.querySelector('.time') as HTMLElement;
    expect(first.querySelector('.time__start')?.textContent?.trim()).toBe('07:00');
    expect(first.querySelector('.time__end')?.textContent?.trim()).toBe('hasta 08:30');
    // Los lectores de pantalla leen el rango completo
    expect(first.getAttribute('aria-label')).toBe('07:00 a 08:30');
  });

  it('muestra la duración y los días de la mentoría', () => {
    const text = (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
    expect(text).toContain('Lunes y miércoles');
    expect(text).toContain('1 h 30 min');
  });
});
