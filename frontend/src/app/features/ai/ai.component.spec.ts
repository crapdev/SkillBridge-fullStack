import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AiComponent } from './ai.component';

describe('AiComponent', () => {
  let component: AiComponent;
  let fixture: ComponentFixture<AiComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [AiComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AiComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
    expect(component.goal).toBe('');
    expect(component.answer).toBe('');
  });

  it('debe asignar el objetivo cuando se hace clic en una sugerencia', () => {
    component.useSuggestion('Quiero mejorar en Docker');
    expect(component.goal).toBe('Quiero mejorar en Docker');
  });

  it('debe enviar la petición a la IA, formatear la respuesta y guardarla en localStorage', () => {
    component.goal = 'Quiero aprender Spring Boot';
    component.ask();

    expect(component.loading).toBeTrue();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ goal: 'Quiero aprender Spring Boot' });

    // Simular respuesta exitosa de la IA en Markdown
    req.flush({ recommendation: '### 1. **Spring Boot Pro**\n- Gran opción.' });

    expect(component.loading).toBeFalse();
    expect(component.answer).toContain('Spring Boot Pro');
    expect(component.formattedAnswer).toBeTruthy();

    // Verificar persistencia en localStorage
    const saved = localStorage.getItem('skillbridge_ai_last_recommendation');
    expect(saved).not.toBeNull();
    expect(JSON.parse(saved!).goal).toBe('Quiero aprender Spring Boot');
  });

  it('debe restaurar la recomendación previa desde localStorage al inicializar', () => {
    const previousState = {
      goal: 'Objetivo previo guardado',
      answer: 'Respuesta previa recuperada',
      savedAt: Date.now()
    };
    localStorage.setItem('skillbridge_ai_last_recommendation', JSON.stringify(previousState));

    // Reinicializar componente
    const newFixture = TestBed.createComponent(AiComponent);
    const newComponent = newFixture.componentInstance;
    newFixture.detectChanges();

    expect(newComponent.goal).toBe('Objetivo previo guardado');
    expect(newComponent.answer).toBe('Respuesta previa recuperada');
    expect(newComponent.formattedAnswer).toBeTruthy();
  });

  it('debe manejar errores HTTP 502/503 y mostrar mensaje de DeepSeek no disponible', () => {
    component.goal = 'Cualquier objetivo';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({ detail: 'DeepSeek no está disponible; inténtalo de nuevo' }, { status: 422, statusText: 'Unprocessable Entity' });

    expect(component.loading).toBeFalse();
    expect(component.error).toBe('DeepSeek no está disponible; inténtalo de nuevo');
    expect(component.answer).toBe('');
  });

  it('debe limpiar las variables y el localStorage al llamar a reset()', () => {
    component.goal = 'Mi objetivo';
    component.answer = 'Una respuesta';
    localStorage.setItem('skillbridge_ai_last_recommendation', JSON.stringify({ goal: 'a', answer: 'b' }));

    component.reset();

    expect(component.goal).toBe('');
    expect(component.answer).toBe('');
    expect(component.formattedAnswer).toBe('');
    expect(localStorage.getItem('skillbridge_ai_last_recommendation')).toBeNull();
  });
});
