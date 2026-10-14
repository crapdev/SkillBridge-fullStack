import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { apiBase } from '../../../core/api';
import { KEEP_SESSION_ON_401 } from '../../../core/auth.interceptor';
import { AdminOffering } from './admin-offering.model';

// Un 401 del panel se muestra como error en la vista en lugar de cerrar la sesión
const context = () => new HttpContext().set(KEEP_SESSION_ON_401, true);

/** Revisión de mentorías (HU-21). Se provee en ADMIN_ROUTES para usar el HttpClient del panel (y sus datos simulados). */
@Injectable()
export class AdminOfferingsService {
  private http = inject(HttpClient);
  private base = () => `${apiBase()}/admin/offerings`;

  /** Pendientes de revisión, la que más lleva esperando primero */
  pending() {
    return this.http.get<AdminOffering[]>(`${this.base()}/pending`, { context: context() });
  }

  approve(id: string) {
    return this.http.patch<AdminOffering>(`${this.base()}/${id}/review`, { decision: 'APPROVED' }, { context: context() });
  }

  /** El motivo es opcional: el proveedor lo ve en su panel para poder corregir la mentoría */
  reject(id: string, reason: string) {
    const body = { decision: 'REJECTED', reason: reason.trim() || null };
    return this.http.patch<AdminOffering>(`${this.base()}/${id}/review`, body, { context: context() });
  }
}
