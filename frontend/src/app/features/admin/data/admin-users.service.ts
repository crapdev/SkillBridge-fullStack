import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { apiBase } from '../../../core/api';
import { KEEP_SESSION_ON_401 } from '../../../core/auth.interceptor';
import { AdminUser, CreateUserRequest, Page, UpdateUserRequest, UserQuery, UserStats } from './admin-user.model';

// Un 401 del panel se muestra como error en la vista en lugar de cerrar la sesión
const context = () => new HttpContext().set(KEEP_SESSION_ON_401, true);

// Se provee en ADMIN_ROUTES para que use el HttpClient del panel (con el interceptor de datos simulados)
@Injectable()
export class AdminUsersService {
  private http = inject(HttpClient);
  private base = () => `${apiBase()}/admin/users`;

  list(query: UserQuery) {
    let params = new HttpParams()
      .set('role', query.role)
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', 'createdAt,desc');
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.status && query.status !== 'ALL') params = params.set('active', query.status === 'ACTIVE');
    return this.http.get<Page<AdminUser>>(this.base(), { params, context: context() });
  }

  stats() { return this.http.get<UserStats>(`${this.base()}/stats`, { context: context() }); }
  get(id: string) { return this.http.get<AdminUser>(`${this.base()}/${id}`, { context: context() }); }
  create(body: CreateUserRequest) { return this.http.post<AdminUser>(this.base(), body, { context: context() }); }
  update(id: string, body: UpdateUserRequest) { return this.http.put<AdminUser>(`${this.base()}/${id}`, body, { context: context() }); }

  // Pendiente de backend: requiere la columna app_users.active
  setStatus(id: string, active: boolean) {
    return this.http.patch<AdminUser>(`${this.base()}/${id}/status`, { active }, { context: context() });
  }
}
