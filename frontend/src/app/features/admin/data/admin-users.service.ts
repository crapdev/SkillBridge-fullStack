import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { apiBase } from '../../../core/api';
import { KEEP_SESSION_ON_401 } from '../../../core/auth.interceptor';
import { AccountStatus, AdminUser, CreateUserRequest, Page, StatusFilter, UpdateUserRequest, UserQuery, UserStats } from './admin-user.model';

/** Estados que pide al backend cada filtro del listado (sin estados = todos) */
const STATUS_FILTERS: Record<StatusFilter, AccountStatus[]> = {
  ALL: [],
  PENDING: ['PENDING_APPROVAL'],
  ACTIVE: ['ACTIVE'],
  INACTIVE: ['INACTIVE', 'REJECTED']
};

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
    for (const status of STATUS_FILTERS[query.status ?? 'ALL']) params = params.append('status', status);
    return this.http.get<Page<AdminUser>>(this.base(), { params, context: context() });
  }

  stats() { return this.http.get<UserStats>(`${this.base()}/stats`, { context: context() }); }
  get(id: string) { return this.http.get<AdminUser>(`${this.base()}/${id}`, { context: context() }); }
  create(body: CreateUserRequest) { return this.http.post<AdminUser>(this.base(), body, { context: context() }); }
  update(id: string, body: UpdateUserRequest) { return this.http.put<AdminUser>(`${this.base()}/${id}`, body, { context: context() }); }

  /** Aprobar, rechazar, desactivar o reactivar; el backend valida que la transición sea permitida */
  setStatus(id: string, status: AccountStatus) {
    return this.http.patch<AdminUser>(`${this.base()}/${id}/status`, { status }, { context: context() });
  }
}
