import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor (sesión expirada)', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  function openSession() {
    auth.restore().subscribe();
    http.expectOne('/api/auth/me').flush({ email: 'laura@test.com', name: 'Laura', role: 'PROVIDER' });
  }

  it('con un 401 cierra la sesión y manda al login avisando que expiró', () => {
    openSession();
    const logout = spyOn(auth, 'logout');
    spyOnProperty(TestBed.inject(Router), 'url').and.returnValue('/provider/offerings');

    TestBed.inject(HttpClient).get('/api/provider/stats').subscribe({ error: () => {} });
    http.expectOne('/api/provider/stats').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalledOnceWith('/login?expired=1&returnUrl=%2Fprovider%2Fofferings');
  });

  it('no avisa expiración cuando no había sesión abierta', () => {
    const logout = spyOn(auth, 'logout');

    TestBed.inject(HttpClient).get('/api/provider/stats').subscribe({ error: () => {} });
    http.expectOne('/api/provider/stats').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).not.toHaveBeenCalled();
  });
});
