import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProviderOfferingsComponent } from './provider-offerings.component';
import { Offering } from '../core/offering.service';

/** SCRUM-175 (HU-20): formulario de la ficha — temario dinámico, días, duración, nivel y aviso de comisión */
describe('ProviderOfferingsComponent · ficha de la mentoría', () => {
  let fixture: ComponentFixture<ProviderOfferingsComponent>;
  let component: ProviderOfferingsComponent;
  let http: HttpTestingController;
  let el: HTMLElement;

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
    http.expectOne('/api/provider/offerings').flush([]);
  });

  afterEach(() => http.verify());

  /** Llena los datos básicos y los 2 temas iniciales */
  function fillValidForm() {
    component.offeringForm.patchValue({
      title: 'Kubernetes', category: 'CLOUD', price: 90000, description: 'Primeros pasos',
      level: 'INTERMEDIO', durationMinutes: 90
    });
    component.syllabus.at(0).setValue({ title: ' Pods ', description: 'Qué son' });
    component.syllabus.at(1).setValue({ title: 'Deployments', description: ' Réplicas ' });
  }

  it('empieza con 2 temas vacíos, 60 minutos, nivel básico y de lunes a viernes', () => {
    expect(component.syllabus.length).toBe(2);
    expect(component.offeringForm.value.durationMinutes).toBe(60);
    expect(component.offeringForm.value.level).toBe('BASICO');
    expect(component.offeringForm.value.workingDays).toEqual(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
  });

  it('exige mínimo 2 temas: no deja quitar por debajo de 2', () => {
    component.removeTopic(0);
    expect(component.syllabus.length).toBe(2);

    component.addTopic();
    expect(component.syllabus.length).toBe(3);
    component.removeTopic(2);
    expect(component.syllabus.length).toBe(2);
  });

  it('un temario con menos de 2 temas deja el formulario inválido', () => {
    fillValidForm();
    expect(component.offeringForm.valid).toBeTrue();

    component.syllabus.removeAt(1);   // saltándose la protección del botón
    expect(component.syllabus.hasError('minTopics')).toBeTrue();
    expect(component.offeringForm.invalid).toBeTrue();
  });

  it('admite máximo 12 temas', () => {
    for (let i = 0; i < 20; i++) component.addTopic();
    expect(component.syllabus.length).toBe(12);
  });

  it('un tema sin título o descripción invalida el formulario', () => {
    fillValidForm();
    component.syllabus.at(1).patchValue({ title: '   ' });
    expect(component.offeringForm.invalid).toBeTrue();
  });

  it('exige al menos un día de la semana', () => {
    fillValidForm();
    component.setDays([]);
    expect(component.offeringForm.invalid).toBeTrue();
    expect(component.showError('workingDays')).toBeTrue();

    component.toggleDay('SATURDAY');
    expect(component.hasDay('SATURDAY')).toBeTrue();
    expect(component.offeringForm.valid).toBeTrue();
  });

  it('muestra el aviso de la comisión del 10% con lo que recibirá el proveedor', () => {
    const notice = () => (el.querySelector('.commission') as HTMLElement).textContent ?? '';
    expect(notice()).toContain('comisión del 10%');

    component.offeringForm.patchValue({ price: 85000 });
    fixture.detectChanges();

    expect(component.earnings()).toEqual({ price: 85000, net: 76500, fee: 8500 });
    expect(notice().replace(/\s/g, ' ')).toContain('76.500');
  });

  it('envía la ficha completa al crear, con textos limpios y días en orden', () => {
    fillValidForm();
    component.setDays(['WEDNESDAY', 'MONDAY']);

    component.onSubmit();

    const req = http.expectOne({ method: 'POST', url: '/api/provider/offerings' });
    expect(req.request.body).toEqual({
      title: 'Kubernetes', description: 'Primeros pasos', category: 'CLOUD', price: 90000,
      syllabus: [{ title: 'Pods', description: 'Qué son' }, { title: 'Deployments', description: 'Réplicas' }],
      durationMinutes: 90, level: 'INTERMEDIO', workingDays: ['MONDAY', 'WEDNESDAY']
    });
    req.flush({});
    http.expectOne('/api/provider/offerings').flush([]);
  });

  it('muestra el motivo del backend si rechaza la ficha (400)', () => {
    fillValidForm();
    component.onSubmit();

    http.expectOne({ method: 'POST', url: '/api/provider/offerings' }).flush(
      { detail: 'syllabus: El temario debe tener al menos 2 temas' }, { status: 400, statusText: 'Bad Request' });

    expect(component.errorMessage).toBe('Revisa los datos: syllabus: El temario debe tener al menos 2 temas');
  });

  it('al editar carga el temario completo, los días, el nivel y la duración', () => {
    const offering: Offering = {
      id: 'o-1', title: 'Java', description: 'Spring', category: 'BACKEND', price: 85000, active: true,
      syllabus: [{ title: 'A', description: 'a' }, { title: 'B', description: 'b' }, { title: 'C', description: 'c' }],
      durationMinutes: 120, level: 'AVANZADO', workingDays: ['FRIDAY', 'TUESDAY']
    };

    component.startEdit(offering);

    expect(component.syllabus.length).toBe(3);
    expect(component.offeringForm.value.syllabus[2]).toEqual({ title: 'C', description: 'c' });
    expect(component.offeringForm.value.durationMinutes).toBe(120);
    expect(component.offeringForm.value.level).toBe('AVANZADO');
    expect(component.offeringForm.value.workingDays).toEqual(['TUESDAY', 'FRIDAY']);
  });

  it('una mentoría antigua con menos de 2 temas se completa con filas vacías para editarla', () => {
    component.startEdit({
      id: 'o-2', title: 'Vieja', description: 'Sin temario', category: 'CLOUD', price: 50000, active: true,
      syllabus: [], durationMinutes: 60, level: 'BASICO', workingDays: ['MONDAY']
    });

    expect(component.syllabus.length).toBe(2);
    expect(component.offeringForm.invalid).toBeTrue();   // debe llenar el temario antes de guardar
  });
});
