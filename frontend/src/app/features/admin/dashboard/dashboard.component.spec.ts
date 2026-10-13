import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AdminUsersService } from '../data/admin-users.service';
import { AdminOfferingsService } from '../data/admin-offerings.service';
import { ToastService } from '../shared/toast.service';

describe('DashboardComponent (HU-23 Task 1)', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let mockUsersService: jasmine.SpyObj<AdminUsersService>;
  let mockToastService: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    mockUsersService = jasmine.createSpyObj<AdminUsersService>('AdminUsersService', [
      'stats',
      'list',
      'get',
      'create',
      'update',
      'setStatus'
    ]);
    mockUsersService.stats.and.returnValue(
      of({
        providers: { total: 10, newThisMonth: 2, active: 8, inactive: 1, pending: 1 },
        customers: { total: 20, newThisMonth: 5, active: 18, inactive: 2, pending: 0 }
      })
    );
    mockUsersService.list.and.returnValue(
      of({
        content: [],
        page: { size: 10, number: 0, totalElements: 0, totalPages: 0 }
      })
    );

    mockToastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AdminUsersService, useValue: mockUsersService },
        // HU-21: el dashboard también muestra las mentorías por revisar
        { provide: AdminOfferingsService, useValue: jasmine.createSpyObj('AdminOfferingsService', { pending: of([]) }) },
        { provide: ToastService, useValue: mockToastService }
      ]
    });

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('Escenario 3: el botón de acción principal del dashboard abre el formulario de nuevo usuario en /admin/usuarios/nuevo', () => {
    const el = fixture.nativeElement as HTMLElement;
    const createBtn = el.querySelector('.adm-page-head a.adm-btn--primary') as HTMLAnchorElement;

    expect(createBtn).not.toBeNull();
    expect(createBtn.textContent?.trim()).toContain('Nuevo usuario');
    expect(createBtn.getAttribute('routerLink')).toBe('/admin/usuarios/nuevo');
  });
});
