import { Routes } from '@angular/router';
import { provideHttpClient, withInterceptors, withRequestsMadeViaParent } from '@angular/common/http';
import { AdminLayoutComponent } from './layout/admin-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { UserListComponent } from './users/user-list.component';
import { UserFormComponent } from './users/user-form.component';
import { adminMockInterceptor } from './data/admin-mock.interceptor';
import { AdminUsersService } from './data/admin-users.service';

const usersRoutes = (path: string, role: 'PROVIDER' | 'CUSTOMER', title: string): Routes => [
  { path, component: UserListComponent, data: { role }, title: `${title} · SkillBridge Admin` },
  { path: `${path}/nuevo`, component: UserFormComponent, data: { role }, title: `Nuevo · ${title} · SkillBridge Admin` },
  { path: `${path}/:id/editar`, component: UserFormComponent, data: { role }, title: `Editar · ${title} · SkillBridge Admin` }
];

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    // HttpClient propio del panel: el mock (solo desarrollo) intercepta primero y el resto de
    // peticiones sigue al HttpClient raíz, que añade el token con authInterceptor
    providers: [provideHttpClient(withInterceptors([adminMockInterceptor]), withRequestsMadeViaParent()), AdminUsersService],
    children: [
      { path: '', component: DashboardComponent, title: 'Dashboard · SkillBridge Admin' },
      ...usersRoutes('proveedores', 'PROVIDER', 'Proveedores'),
      ...usersRoutes('clientes', 'CUSTOMER', 'Clientes'),
      { path: '**', redirectTo: '' }
    ]
  }
];
