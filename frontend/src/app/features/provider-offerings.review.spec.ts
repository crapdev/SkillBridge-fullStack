import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ProviderOfferingsComponent } from './provider-offerings.component';
import { Offering } from '../core/offering.service';
import { REVIEW_ESTIMATE } from '../shared/offering-review';

/** SCRUM-181 (HU-21): estado de revisión, motivo de rechazo y aviso "sin horarios" en el panel del proveedor */
describe('ProviderOfferingsComponent · revisión de mentorías', () => {
  let fixture: ComponentFixture<ProviderOfferingsComponent>;
  let component: ProviderOfferingsComponent;
  let http: HttpTestingController;
  let el: HTMLElement;

  const profile = {
    syllabus: [{ title: 'JPA', description: 'Entidades' }, { title: 'Seguridad', description: 'JWT' }],
    durationMinutes: 60, level: 'INTERMEDIO' as const, workingDays: ['MONDAY' as const]
  };
  const base = { description: 'Spring Boot', category: 'BACKEND', price: 85000, active: true, ...profile };
  const pending: Offering = { id: 'p', title: 'Pendiente', ...base, reviewStatus: 'PENDING_REVIEW', rejectionReason: null };
  const rejected: Offering = { id: 'r', title: 'Rechazada', ...base, reviewStatus: 'REJECTED', rejectionReason: 'Falta el temario de seguridad' };
  const published: Offering = { id: 'a', title: 'Publicada', ...base, reviewStatus: 'APPROVED', rejectionReason: null };
  const noSlots: Offering = { id: 'n', title: 'Sin horarios', ...base, reviewStatus: 'APPROVED', rejectionReason: null };

  const freeSlot = { id: 's', offeringId: 'x', scheduledAt: '2030-01-01T15:00:00Z', reserved: false };
  const reservedSlot = { ...freeSlot, reserved: true };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProviderOfferingsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ProviderOfferingsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    fixture.detectChanges();

    http.expectOne('/api/provider/offerings').flush([pending, rejected, published, noSlots]);
    http.expectOne('/api/provider/offerings/p/slots').flush([freeSlot]);
    http.expectOne('/api/provider/offerings/r/slots').flush([]);
    http.expectOne('/api/provider/offerings/a/slots').flush([freeSlot, reservedSlot]);
    // Solo tiene un horario y ya está reservado: no le queda ninguno libre
    http.expectOne('/api/provider/offerings/n/slots').flush([reservedSlot]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const item = (title: string) =>
    Array.from(el.querySelectorAll('.offering-item')).find(i => i.querySelector('h3')?.textContent?.trim() === title)!;
  const text = (title: string) => item(title).textContent!.replace(/\s+/g, ' ');

  it('una mentoría en revisión muestra el estado y el tiempo estimado', () => {
    expect(text('Pendiente')).toContain('En revisión');
    expect(text('Pendiente')).toContain(REVIEW_ESTIMATE);
  });

  it('una rechazada muestra el motivo y ofrece corregirla', () => {
    expect(text('Rechazada')).toContain('Motivo: Falta el temario de seguridad');
    expect(text('Rechazada')).toContain('Corregir');
    // El aviso de horarios no se suma al del rechazo
    expect(text('Rechazada')).not.toContain('Sin horarios disponibles');
  });

  it('una aprobada con horarios libres es visible en el catálogo', () => {
    expect(text('Publicada')).toContain('Aprobada');
    expect(text('Publicada')).toContain('Visible en el catálogo');
  });

  it('una aprobada sin horarios libres avisa que los clientes no la ven', () => {
    expect(text('Sin horarios')).toContain('Sin horarios disponibles');
    expect(text('Sin horarios')).not.toContain('Visible en el catálogo');
    expect(item('Sin horarios').querySelector('.notice a')?.getAttribute('href')).toBe('/provider/offerings/n/horarios');
  });

  it('antes de publicar avisa que la mentoría quedará en revisión', () => {
    expect(el.querySelector('form .notice--info')?.textContent).toContain('en revisión');
  });

  it('al corregir una rechazada avisa que se envió de nuevo a revisión', () => {
    component.startEdit(rejected);
    fixture.detectChanges();
    expect(el.querySelector('form .notice--info')?.textContent).toContain('de nuevo a revisión');

    component.onSubmit();
    http.expectOne('/api/provider/offerings/r').flush({ ...rejected, reviewStatus: 'PENDING_REVIEW', rejectionReason: null });

    expect(component.successMessage).toContain('enviados de nuevo a revisión');
  });
});

/** SCRUM-186 (HU-22): el dashboard enlaza a /provider/offerings?editar=<id> */
describe('ProviderOfferingsComponent · enlace de edición desde el dashboard', () => {
  const offering: Offering = {
    id: 'r', title: 'Rechazada', description: 'Spring Boot', category: 'BACKEND', price: 85000, active: true,
    syllabus: [{ title: 'JPA', description: 'Entidades' }], durationMinutes: 60, level: 'INTERMEDIO', workingDays: ['MONDAY'],
    reviewStatus: 'REJECTED', rejectionReason: 'Falta temario'
  };

  it('abre la mentoría indicada en el formulario de edición', async () => {
    await TestBed.configureTestingModule({
      imports: [ProviderOfferingsComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ editar: 'r' }) } } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ProviderOfferingsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/provider/offerings').flush([offering]);
    http.expectOne('/api/provider/offerings/r/slots').flush([]);

    expect(fixture.componentInstance.editingId).toBe('r');
    http.verify();
  });
});
