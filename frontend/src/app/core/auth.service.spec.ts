import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService (HU-25 Chat History Cleanup on Logout)', () => {
  let service: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });
    service = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('debe limpiar el historial del chat de IA en localStorage al cerrar sesión (logout)', () => {
    const navigateSpy = spyOn(router, 'navigateByUrl');

    localStorage.setItem('skillbridge_token', 'test-token');
    localStorage.setItem('skillbridge_ai_chat_history', JSON.stringify([{ role: 'user', content: 'test' }]));
    localStorage.setItem('skillbridge_ai_last_recommendation', JSON.stringify({ goal: 'antiguo' }));

    service.logout('/login');

    expect(localStorage.getItem('skillbridge_token')).toBeNull();
    expect(localStorage.getItem('skillbridge_ai_chat_history')).toBeNull();
    expect(localStorage.getItem('skillbridge_ai_last_recommendation')).toBeNull();
    expect(service.authenticated()).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
