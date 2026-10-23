import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AiComponent } from './ai.component';

describe('AiComponent (HU-25 Asistente IA Conversacional)', () => {
  let component: AiComponent;
  let fixture: ComponentFixture<AiComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

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
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('debe crearse con estado inicial vacío (sin mensajes ni errores)', () => {
    expect(component).toBeTruthy();
    expect(component.goal).toBe('');
    expect(component.messages.length).toBe(0);
    expect(component.loading).toBeFalse();
    expect(component.error).toBe('');
    expect(component.answer).toBe('');
  });

  it('debe asignar el objetivo cuando se hace clic en una sugerencia', () => {
    component.useSuggestion('Quiero mejorar en Docker');
    expect(component.goal).toBe('Quiero mejorar en Docker');
  });

  it('debe enviar la primera consulta con history vacío y agregar el mensaje del usuario y del asistente', () => {
    component.goal = 'Quiero aprender Spring Boot';
    component.ask();

    expect(component.loading).toBeTrue();
    expect(component.messages.length).toBe(1);
    expect(component.messages[0].role).toBe('user');
    expect(component.messages[0].content).toBe('Quiero aprender Spring Boot');

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      goal: 'Quiero aprender Spring Boot',
      history: []
    });

    req.flush({ recommendation: 'Te sugiero ver la sesión [Spring Boot Pro](/services/12).' });

    expect(component.loading).toBeFalse();
    expect(component.messages.length).toBe(2);
    expect(component.messages[1].role).toBe('assistant');
    expect(component.messages[1].content).toContain('Spring Boot Pro');
    expect(component.answer).toContain('Spring Boot Pro');

    // Verificar persistencia en localStorage
    const saved = localStorage.getItem('skillbridge_ai_chat_history');
    expect(saved).not.toBeNull();
    const parsed = JSON.parse(saved!);
    expect(parsed.length).toBe(2);
    expect(parsed[0].role).toBe('user');
    expect(parsed[1].role).toBe('assistant');
  });

  it('debe incluir el historial previo en peticiones consecutivas (contexto conversacional scrum-197 y scrum-199)', () => {
    // 1er turno
    component.goal = 'Hola, busco mentoría de React';
    component.ask();
    let req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({ recommendation: 'Tenemos [React Avanzado](/services/8).' });

    // 2do turno con seguimiento
    component.goal = '¿En qué horarios está disponible?';
    component.ask();

    req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    expect(req.request.body).toEqual({
      goal: '¿En qué horarios está disponible?',
      history: [
        { role: 'user', content: 'Hola, busco mentoría de React' },
        { role: 'assistant', content: 'Tenemos [React Avanzado](/services/8).' }
      ]
    });

    req.flush({ recommendation: 'Está disponible de lunes a viernes en las tardes.' });

    expect(component.messages.length).toBe(4);
    expect(component.messages[2].role).toBe('user');
    expect(component.messages[2].content).toBe('¿En qué horarios está disponible?');
    expect(component.messages[3].role).toBe('assistant');
  });

  it('debe renderizar enlaces a las mentorías recomendadas y permitir navegación SPA (scrum-200)', () => {
    const navigateSpy = spyOn(router, 'navigateByUrl');

    component.goal = 'Recomiéndame mentorías';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({ recommendation: 'Te recomiendo [Mentoría Java](/services/42).' });

    fixture.detectChanges();

    const asstMsg = component.messages.find(m => m.role === 'assistant');
    expect(asstMsg).toBeTruthy();

    // Simular clic en el enlace generado por markdown
    const dummyLink = document.createElement('a');
    dummyLink.setAttribute('href', '/services/42');
    dummyLink.className = 'chat-offering-link';
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true });
    Object.defineProperty(clickEvent, 'target', { value: dummyLink, enumerable: true });

    component.onMessageClick(clickEvent);

    expect(navigateSpy).toHaveBeenCalledWith('/services/42');
  });

  it('debe mostrar "Por ahora no hay mentorias disponibles" con enlace al catálogo cuando la lista de recomendaciones venga vacía (BUG-02)', () => {
    component.goal = 'Quiero aprender algo';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({ recommendation: '' });

    fixture.detectChanges();

    expect(component.answer).toContain('Por ahora no hay mentorias disponibles');
    const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/servicios"]');
    expect(link).toBeTruthy();
  });

  it('debe vaciar el historial local y en memoria al presionar Nueva Conversación (reset)', () => {
    component.messages = [
      { id: '1', role: 'user', content: 'Hola', timestamp: Date.now() },
      { id: '2', role: 'assistant', content: 'Hola!', timestamp: Date.now() }
    ];
    component.goal = 'Algo escrito';
    localStorage.setItem('skillbridge_ai_chat_history', JSON.stringify([{ role: 'user', content: 'Hola' }]));
    localStorage.setItem('skillbridge_ai_last_recommendation', JSON.stringify({ goal: 'antiguo' }));

    component.reset();

    expect(component.messages.length).toBe(0);
    expect(component.goal).toBe('');
    expect(component.answer).toBe('');
    expect(localStorage.getItem('skillbridge_ai_chat_history')).toBeNull();
    expect(localStorage.getItem('skillbridge_ai_last_recommendation')).toBeNull();
  });

  it('debe restaurar el historial previo desde localStorage al inicializar el componente', () => {
    const storedHistory = [
      { role: 'user', content: 'Consulta previa guardada', timestamp: 1000 },
      { role: 'assistant', content: 'Respuesta previa guardada', timestamp: 2000 }
    ];
    localStorage.setItem('skillbridge_ai_chat_history', JSON.stringify(storedHistory));

    const newFixture = TestBed.createComponent(AiComponent);
    const newComponent = newFixture.componentInstance;
    newFixture.detectChanges();

    expect(newComponent.messages.length).toBe(2);
    expect(newComponent.messages[0].content).toBe('Consulta previa guardada');
    expect(newComponent.messages[1].content).toBe('Respuesta previa guardada');
    expect(newComponent.answer).toBe('Respuesta previa guardada');
  });

  it('debe manejar error 401 de usuario no autenticado', () => {
    component.goal = 'Pregunta sin auth';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(component.loading).toBeFalse();
    expect(component.unauthorized).toBeTrue();
    expect(component.error).toContain('Necesitas iniciar sesión');
  });

  it('debe manejar errores del proveedor de IA o servidor (HU-12 / scrum-201)', () => {
    component.goal = 'Pregunta con caída';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush({ detail: 'DeepSeek no está disponible en este momento' }, { status: 502, statusText: 'Bad Gateway' });

    expect(component.loading).toBeFalse();
    expect(component.error).toBe('DeepSeek no está disponible en este momento');
  });

  it('debe enviar al presionar Enter simple y no enviar al presionar Shift+Enter', () => {
    spyOn(component, 'ask');

    // Shift + Enter: no debe llamar a ask()
    const shiftEnter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true });
    component.handleEnter(shiftEnter);
    expect(component.ask).not.toHaveBeenCalled();

    // Enter simple: debe llamar a ask()
    const enter = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: false, cancelable: true });
    component.handleEnter(enter);
    expect(component.ask).toHaveBeenCalledTimes(1);
  });

  it('debe usar mensaje fallback claro de saturación (429) cuando no viene cuerpo de error (scrum-201)', () => {
    component.goal = 'Pregunta con saturación';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush(null, { status: 429, statusText: 'Too Many Requests' });

    expect(component.loading).toBeFalse();
    expect(component.error).toBe('El asistente recibió demasiadas consultas; espera un momento e inténtalo de nuevo.');
  });

  it('debe usar mensaje fallback claro de indisponibilidad (503) cuando no viene cuerpo de error (scrum-201)', () => {
    component.goal = 'Pregunta con caída de DeepSeek';
    component.ask();

    const req = httpMock.expectOne(r => r.url.endsWith('/ai/recommendations'));
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(component.loading).toBeFalse();
    expect(component.error).toBe('DeepSeek no está disponible en este momento; inténtalo de nuevo.');
  });

  it('no debe navegar si se hace clic fuera de enlaces de mentoría', () => {
    const navigateSpy = spyOn(router, 'navigateByUrl');
    const dummyDiv = document.createElement('div');
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true });
    Object.defineProperty(clickEvent, 'target', { value: dummyDiv, enumerable: true });

    component.onMessageClick(clickEvent);

    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
