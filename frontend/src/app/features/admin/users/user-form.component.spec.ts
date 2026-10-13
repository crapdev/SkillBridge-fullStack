import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { UserFormComponent } from './user-form.component';
import { AdminUsersService } from '../data/admin-users.service';
import { ToastService } from '../shared/toast.service';
import { AdminUser, ManagedRole } from '../data/admin-user.model';

function createMockUser(data: Partial<AdminUser> & { id: string; name: string; email: string; role: ManagedRole }): AdminUser {
  return {
    status: 'ACTIVE',
    active: true,
    createdAt: new Date().toISOString(),
    ...data
  };
}

describe('UserFormComponent (HU-23)', () => {
  let mockUsersService: jasmine.SpyObj<AdminUsersService>;
  let mockToastService: jasmine.SpyObj<ToastService>;
  let router: Router;

  function createComponent(
    routeData: { role?: ManagedRole },
    paramId: string | null = null,
    userToLoad: AdminUser | null = null
  ): {
    fixture: ComponentFixture<UserFormComponent>;
    component: UserFormComponent;
  } {
    TestBed.resetTestingModule();

    mockUsersService = jasmine.createSpyObj<AdminUsersService>('AdminUsersService', ['get', 'create', 'update']);
    if (userToLoad) {
      mockUsersService.get.and.returnValue(of(userToLoad));
    }
    mockToastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    const activatedRouteStub = {
      snapshot: {
        paramMap: convertToParamMap(paramId ? { id: paramId } : {}),
        data: routeData
      }
    };

    TestBed.configureTestingModule({
      imports: [UserFormComponent],
      providers: [
        provideRouter([]),
        { provide: AdminUsersService, useValue: mockUsersService },
        { provide: ToastService, useValue: mockToastService },
        { provide: ActivatedRoute, useValue: activatedRouteStub }
      ]
    });

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.stub();

    const fixture = TestBed.createComponent(UserFormComponent);
    const component = fixture.componentInstance;
    return { fixture, component };
  }

  describe('Escenario 1: Crear proveedor desde sección Proveedores', () => {
    it('no muestra el selector de rol y crea al usuario con rol PROVIDER', () => {
      const { fixture, component } = createComponent({ role: 'PROVIDER' });
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const roleSelect = el.querySelector('#f-role');
      expect(roleSelect).toBeNull();
      expect(component['showRoleSelector']()).toBeFalse();
      expect(component['form'].controls.role.value).toBe('PROVIDER');

      const createdUser = createMockUser({
        id: '1',
        name: 'Carlos Proveedor',
        email: 'carlos@proveedor.com',
        role: 'PROVIDER'
      });
      mockUsersService.create.and.returnValue(of(createdUser));

      component['form'].controls.name.setValue('Carlos Proveedor');
      component['form'].controls.email.setValue('carlos@proveedor.com');
      component['form'].controls.password.setValue('password123');

      component['submit']();

      expect(mockUsersService.create).toHaveBeenCalledWith({
        name: 'Carlos Proveedor',
        email: 'carlos@proveedor.com',
        password: 'password123',
        role: 'PROVIDER'
      });
      expect(router.navigate).toHaveBeenCalledWith(['/admin', 'proveedores']);
    });
  });

  describe('Escenario 2: Crear cliente desde sección Clientes', () => {
    it('no muestra el selector de rol y crea al usuario con rol CUSTOMER', () => {
      const { fixture, component } = createComponent({ role: 'CUSTOMER' });
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const roleSelect = el.querySelector('#f-role');
      expect(roleSelect).toBeNull();
      expect(component['showRoleSelector']()).toBeFalse();
      expect(component['form'].controls.role.value).toBe('CUSTOMER');

      const createdUser = createMockUser({
        id: '2',
        name: 'Lucia Cliente',
        email: 'lucia@cliente.com',
        role: 'CUSTOMER'
      });
      mockUsersService.create.and.returnValue(of(createdUser));

      component['form'].controls.name.setValue('Lucia Cliente');
      component['form'].controls.email.setValue('lucia@cliente.com');
      component['form'].controls.password.setValue('password123');

      component['submit']();

      expect(mockUsersService.create).toHaveBeenCalledWith({
        name: 'Lucia Cliente',
        email: 'lucia@cliente.com',
        password: 'password123',
        role: 'CUSTOMER'
      });
      expect(router.navigate).toHaveBeenCalledWith(['/admin', 'clientes']);
    });
  });

  describe('Escenario 3: Crear usuario desde el dashboard principal', () => {
    it('muestra el selector de rol y permite seleccionar y crear un Cliente', () => {
      const { fixture, component } = createComponent({});
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const roleSelect = el.querySelector('#f-role') as HTMLSelectElement;
      expect(roleSelect).not.toBeNull();
      expect(component['showRoleSelector']()).toBeTrue();

      const createdUser = createMockUser({
        id: '3',
        name: 'Nuevo Cliente',
        email: 'nuevo@cliente.com',
        role: 'CUSTOMER'
      });
      mockUsersService.create.and.returnValue(of(createdUser));

      component['form'].controls.name.setValue('Nuevo Cliente');
      component['form'].controls.email.setValue('nuevo@cliente.com');
      component['form'].controls.password.setValue('password123');
      component['form'].controls.role.setValue('CUSTOMER');

      component['submit']();

      expect(mockUsersService.create).toHaveBeenCalledWith({
        name: 'Nuevo Cliente',
        email: 'nuevo@cliente.com',
        password: 'password123',
        role: 'CUSTOMER'
      });
      expect(router.navigate).toHaveBeenCalledWith(['/admin', 'clientes']);
    });

    it('muestra el selector de rol y permite seleccionar y crear un Proveedor', () => {
      const { fixture, component } = createComponent({});
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const roleSelect = el.querySelector('#f-role') as HTMLSelectElement;
      expect(roleSelect).not.toBeNull();

      const createdUser = createMockUser({
        id: '4',
        name: 'Nuevo Proveedor',
        email: 'nuevo@proveedor.com',
        role: 'PROVIDER'
      });
      mockUsersService.create.and.returnValue(of(createdUser));

      component['form'].controls.name.setValue('Nuevo Proveedor');
      component['form'].controls.email.setValue('nuevo@proveedor.com');
      component['form'].controls.password.setValue('password123');
      component['form'].controls.role.setValue('PROVIDER');

      component['submit']();

      expect(mockUsersService.create).toHaveBeenCalledWith({
        name: 'Nuevo Proveedor',
        email: 'nuevo@proveedor.com',
        password: 'password123',
        role: 'PROVIDER'
      });
      expect(router.navigate).toHaveBeenCalledWith(['/admin', 'proveedores']);
    });
  });

  describe('Escenario 4: Editar usuarios desde secciones específicas', () => {
    it('conserva el rol Proveedor al editar desde la sección Proveedores y oculta el selector de rol', () => {
      const existingProvider = createMockUser({
        id: 'prov-1',
        name: 'Ana Proveedora',
        email: 'ana@proveedora.com',
        role: 'PROVIDER',
        createdAt: '2026-01-01T00:00:00Z'
      });

      const { fixture, component } = createComponent({ role: 'PROVIDER' }, 'prov-1', existingProvider);
      mockUsersService.update.and.returnValue(of({ ...existingProvider, name: 'Ana Editada' }));

      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('#f-role')).toBeNull();
      expect(component['showRoleSelector']()).toBeFalse();
      expect(component['form'].controls.role.value).toBe('PROVIDER');

      component['form'].controls.name.setValue('Ana Editada');
      component['submit']();

      expect(mockUsersService.update).toHaveBeenCalledWith('prov-1', {
        name: 'Ana Editada',
        email: 'ana@proveedora.com',
        role: 'PROVIDER'
      });
    });

    it('conserva el rol Cliente al editar desde la sección Clientes y oculta el selector de rol', () => {
      const existingCustomer = createMockUser({
        id: 'cust-1',
        name: 'Pedro Cliente',
        email: 'pedro@cliente.com',
        role: 'CUSTOMER',
        createdAt: '2026-01-01T00:00:00Z'
      });

      const { fixture, component } = createComponent({ role: 'CUSTOMER' }, 'cust-1', existingCustomer);
      mockUsersService.update.and.returnValue(of({ ...existingCustomer, name: 'Pedro Editado' }));

      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('#f-role')).toBeNull();
      expect(component['showRoleSelector']()).toBeFalse();
      expect(component['form'].controls.role.value).toBe('CUSTOMER');

      component['form'].controls.name.setValue('Pedro Editado');
      component['submit']();

      expect(mockUsersService.update).toHaveBeenCalledWith('cust-1', {
        name: 'Pedro Editado',
        email: 'pedro@cliente.com',
        role: 'CUSTOMER'
      });
    });
  });

  describe('Escenario 5: Editar desde el dashboard', () => {
    it('muestra el selector de rol y guarda el cambio de rol si se modifica', () => {
      const existingUser = createMockUser({
        id: 'user-1',
        name: 'Maria Usuario',
        email: 'maria@usuario.com',
        role: 'PROVIDER',
        createdAt: '2026-01-01T00:00:00Z'
      });

      const { fixture, component } = createComponent({}, 'user-1', existingUser);
      mockUsersService.update.and.returnValue(of({ ...existingUser, role: 'CUSTOMER' }));

      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const roleSelect = el.querySelector('#f-role');
      expect(roleSelect).not.toBeNull();
      expect(component['showRoleSelector']()).toBeTrue();
      expect(component['form'].controls.role.value).toBe('PROVIDER');

      // Modificamos el rol a CUSTOMER
      component['form'].controls.role.setValue('CUSTOMER');
      component['submit']();

      expect(mockUsersService.update).toHaveBeenCalledWith('user-1', {
        name: 'Maria Usuario',
        email: 'maria@usuario.com',
        role: 'CUSTOMER'
      });
    });

    it('conserva el rol original si no se modifica al editar desde el dashboard', () => {
      const existingUser = createMockUser({
        id: 'user-2',
        name: 'Jose Usuario',
        email: 'jose@usuario.com',
        role: 'PROVIDER',
        createdAt: '2026-01-01T00:00:00Z'
      });

      const { fixture, component } = createComponent({}, 'user-2', existingUser);
      mockUsersService.update.and.returnValue(of({ ...existingUser, name: 'Jose Editado' }));

      fixture.detectChanges();

      component['form'].controls.name.setValue('Jose Editado');
      component['submit']();

      expect(mockUsersService.update).toHaveBeenCalledWith('user-2', {
        name: 'Jose Editado',
        email: 'jose@usuario.com',
        role: 'PROVIDER'
      });
    });
  });

  describe('Navegación y Cancelación', () => {
    it('cancela y regresa al listado correspondiente cuando se accede desde una sección', () => {
      const { fixture, component } = createComponent({ role: 'PROVIDER' });
      fixture.detectChanges();

      component['cancel']();
      expect(router.navigate).toHaveBeenCalledWith(['/admin', 'proveedores']);
    });

    it('cancela y regresa al dashboard principal cuando se accede desde el dashboard', () => {
      const { fixture, component } = createComponent({});
      fixture.detectChanges();

      component['cancel']();
      expect(router.navigate).toHaveBeenCalledWith(['/admin']);
    });
  });
});
