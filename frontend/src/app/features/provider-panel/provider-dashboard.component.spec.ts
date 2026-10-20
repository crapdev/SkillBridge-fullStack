import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProviderDashboardComponent } from './provider-dashboard.component';
import { ProviderStats } from './provider-stats.service';
import { Offering } from '../../core/offering.service';

/** SCRUM-186 (HU-22): indicadores y accesos rápidos del dashboard del proveedor */
describe('ProviderDashboardComponent', () => {
  let fixture: ComponentFixture<ProviderDashboardComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const stats: ProviderStats = {
    offerings: { total: 3, approved: 1, pendingReview: 1, rejected: 1, approvedWithoutFreeSlots: 1 },
    slots: { upcomingFree: 4, upcomingReserved: 2 },
    bookings: { total: 5, upcoming: 2, pendingPayment: 1, confirmed: 1, completed: 3, cancelled: 0 }
  };
  const base = {
    description: 'Spring Boot', category: 'BACKEND', price: 85000, active: true,
    syllabus: [], durationMinutes: 60, level: 'INTERMEDIO' as const, workingDays: [], rejectionReason: null
  };
  const approved: Offering = { id: 'a', title: 'Aprobada', ...base, reviewStatus: 'APPROVED' };
  const pending: Offering = { id: 'p', title: 'Pendiente', ...base, reviewStatus: 'PENDING_REVIEW' };
  const rejected: Offering = { id: 'r', title: 'Rechazada', ...base, reviewStatus: 'REJECTED', rejectionReason: 'Falta temario' };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProviderDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ProviderDashboardComponent);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const flush = (offerings: Offering[] = [approved, pending, rejected]) => {
    http.expectOne('/api/provider/stats').flush(stats);
    http.expectOne('/api/provider/offerings').flush(offerings);
    fixture.detectChanges();
  };
  const text = (selector: string) => el.querySelector(selector)?.textContent?.replace(/\s+/g, ' ') ?? '';

  it('muestra los indicadores de mentorías, horarios y reservas', () => {
    flush();
    const values = Array.from(el.querySelectorAll('.adm-kpi__value')).map(v => v.textContent?.trim());
    expect(values).toEqual(['3', '4', '2', '2']);
    expect(text('.adm-kpis')).toContain('1 aprobadas · 1 en revisión · 1 rechazadas');
  });

  it('avisa de las mentorías sin horarios, rechazadas y en revisión', () => {
    flush();
    expect(text('.alerts')).toContain('1 mentoría aprobada no tiene horarios libres');
    expect(text('.alerts')).toContain('1 mentoría fue rechazada');
    expect(text('.alerts')).toContain('1 mentoría está en revisión');
  });

  it('ordena primero las que necesitan atención y enlaza a editar y a horarios', () => {
    flush();
    const rows = Array.from(el.querySelectorAll('.list__rows li'));
    expect(rows.map(r => r.querySelector('strong')?.textContent)).toEqual(['Rechazada', 'Pendiente', 'Aprobada']);

    const links = Array.from(rows[0].querySelectorAll('a')).map(a => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(links).toEqual([['Corregir', '/provider/offerings?editar=r'], ['Horarios', '/provider/offerings/r/horarios']]);
  });

  it('sin mentorías invita a crear la primera', () => {
    flush([]);
    expect(text('.list__state')).toContain('Aún no tienes mentorías');
    expect(el.querySelector('.list__state a')?.getAttribute('href')).toBe('/provider/offerings');
  });

  it('si fallan los indicadores ofrece reintentar', () => {
    http.expectOne('/api/provider/stats').flush(null, { status: 500, statusText: 'Error' });
    http.expectOne('/api/provider/offerings').flush([]);
    fixture.detectChanges();
    expect(text('[role=alert]')).toContain('No fue posible cargar tus indicadores');

    (el.querySelector('[role=alert] button') as HTMLButtonElement).click();
    flush();
    expect(el.querySelector('[role=alert]')).toBeNull();
  });
});
