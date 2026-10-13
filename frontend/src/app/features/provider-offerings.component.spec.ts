import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProviderOfferingsComponent } from './provider-offerings.component';
import { Offering } from '../core/offering.service';
import { AlertService } from '../shared/alert.service';

/** SCRUM-168 (HU-19): edición de mentorías y confirmación al eliminar en el panel del proveedor */
describe('ProviderOfferingsComponent', () => {
  let fixture: ComponentFixture<ProviderOfferingsComponent>;
  let component: ProviderOfferingsComponent;
  let http: HttpTestingController;
  let alerts: AlertService;

  // Ficha de la mentoría (HU-20)
  const profile = {
    syllabus: [{ title: 'JPA', description: 'Entidades' }, { title: 'Seguridad', description: 'JWT' }],
    durationMinutes: 60, level: 'INTERMEDIO' as const, workingDays: ['MONDAY' as const, 'WEDNESDAY' as const]
  };
  const java: Offering = {
    id: 'o-1', title: 'Mentoría Java', description: 'Spring Boot', category: 'BACKEND', price: 85000, active: true, ...profile
  };
  const angular: Offering = {
    id: 'o-2', title: 'Mentoría Angular', description: 'Signals', category: 'FRONTEND', price: 75000, active: true, ...profile
  };

  const slot = (reserved: boolean) => ({
    id: crypto.randomUUID(), offeringId: java.id, scheduledAt: '2030-01-01T15:00:00Z', reserved
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProviderOfferingsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ProviderOfferingsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    alerts = TestBed.inject(AlertService);

    fixture.detectChanges();
    http.expectOne('/api/provider/offerings').flush([java, angular]);
  });

  afterEach(() => http.verify());

  // ==========================================
  // EDICIÓN (SCRUM-166)
  // ==========================================

  it('carga la mentoría en el formulario al pulsar Editar', () => {
    component.startEdit(java);

    expect(component.isEditing).toBeTrue();
    expect(component.offeringForm.value).toEqual({
      title: 'Mentoría Java', category: 'BACKEND', price: 85000, description: 'Spring Boot', ...profile
    });
    expect(component.submitLabel).toBe('Guardar cambios');
  });

  it('envía PUT con los datos limpios y actualiza la lista', () => {
    component.startEdit(java);
    component.offeringForm.patchValue({ title: '  Java avanzado  ', price: 120000 });

    component.onSubmit();

    const req = http.expectOne('/api/provider/offerings/o-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      title: 'Java avanzado', description: 'Spring Boot', category: 'BACKEND', price: 120000, ...profile
    });
    req.flush({ ...java, title: 'Java avanzado', price: 120000 });

    expect(component.myOfferings.find(o => o.id === 'o-1')?.title).toBe('Java avanzado');
    expect(component.isEditing).toBeFalse();
    expect(component.successMessage).toContain('Cambios guardados');
  });

  it('no envía la edición si el precio no es mayor a cero', () => {
    component.startEdit(java);
    component.offeringForm.patchValue({ price: 0 });

    component.onSubmit();

    expect(component.offeringForm.invalid).toBeTrue();
    expect(component.showError('price')).toBeTrue();
    http.expectNone('/api/provider/offerings/o-1');
  });

  it('no envía la edición si un campo obligatorio solo tiene espacios', () => {
    component.startEdit(java);
    component.offeringForm.patchValue({ title: '   ' });

    component.onSubmit();

    expect(component.showError('title')).toBeTrue();
    http.expectNone('/api/provider/offerings/o-1');
  });

  it('muestra un mensaje claro y sale del modo edición si la mentoría es de otro proveedor (403)', () => {
    component.startEdit(java);
    component.onSubmit();

    http.expectOne('/api/provider/offerings/o-1')
      .flush({ detail: 'No tienes permisos' }, { status: 403, statusText: 'Forbidden' });

    expect(component.errorMessage).toBe('No puedes editar una mentoría que pertenece a otro proveedor.');
    expect(component.isEditing).toBeFalse();
    http.expectOne('/api/provider/offerings').flush([angular]);
  });

  it('cancelar la edición vuelve al modo crear', () => {
    component.startEdit(java);
    component.cancelEdit();

    expect(component.isEditing).toBeFalse();
    expect(component.offeringForm.value.title).toBe('');
    expect(component.submitLabel).toBe('Publicar Mentoría');
  });

  // ==========================================
  // ELIMINACIÓN (SCRUM-167)
  // ==========================================

  it('no pide confirmación y explica el motivo si la mentoría tiene horarios reservados', async () => {
    const confirm = spyOn(alerts, 'confirm');
    const error = spyOn(alerts, 'error');

    const done = component.deleteOffering(java);
    http.expectOne('/api/provider/offerings/o-1/slots').flush([slot(true), slot(false)]);
    await done;

    expect(confirm).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith('No se puede eliminar esta mentoría', jasmine.stringContaining('1 horario reservado'));
    http.expectNone({ method: 'DELETE', url: '/api/provider/offerings/o-1' });
  });

  it('pide confirmación indicando los horarios libres y no elimina si el proveedor cancela', async () => {
    const confirm = spyOn(alerts, 'confirm').and.resolveTo(false);

    const done = component.deleteOffering(java);
    http.expectOne('/api/provider/offerings/o-1/slots').flush([slot(false), slot(false)]);
    await done;

    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({
      title: '¿Eliminar "Mentoría Java"?',
      danger: true,
      message: jasmine.stringContaining('2 horarios libres')
    }));
    expect(confirm.calls.mostRecent().args[0].message)
      .toContain('no se puede eliminar una mentoría con horarios reservados');
    http.expectNone({ method: 'DELETE', url: '/api/provider/offerings/o-1' });
  });

  it('elimina tras confirmar, la quita de la lista y avisa con éxito', async () => {
    spyOn(alerts, 'confirm').and.resolveTo(true);
    const success = spyOn(alerts, 'success');

    const done = component.deleteOffering(java);
    http.expectOne('/api/provider/offerings/o-1/slots').flush([]);
    await done;

    expect(component.deletingId).toBe('o-1');
    const req = http.expectOne({ method: 'DELETE', url: '/api/provider/offerings/o-1' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(component.myOfferings.map(o => o.id)).toEqual(['o-2']);
    expect(component.deletingId).toBeNull();
    expect(success).toHaveBeenCalledWith('Mentoría eliminada', jasmine.any(String));
  });

  it('muestra el motivo del backend si rechaza la eliminación (422)', async () => {
    spyOn(alerts, 'confirm').and.resolveTo(true);
    const error = spyOn(alerts, 'error');

    const done = component.deleteOffering(java);
    http.expectOne('/api/provider/offerings/o-1/slots').flush([]);
    await done;

    http.expectOne({ method: 'DELETE', url: '/api/provider/offerings/o-1' }).flush(
      { detail: 'No puedes eliminar una mentoría que tiene horarios reservados' },
      { status: 422, statusText: 'Unprocessable Entity' }
    );

    expect(error).toHaveBeenCalledWith(
      'No se puede eliminar esta mentoría', 'No puedes eliminar una mentoría que tiene horarios reservados'
    );
    expect(component.myOfferings.length).toBe(2);
  });
});
