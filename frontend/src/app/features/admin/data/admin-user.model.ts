/**
 * Contrato que el frontend espera de /api/admin/users (ver docs/ADMIN-PANEL.md).
 * Todo sale de la tabla app_users salvo `active`, que aún no existe en la base de datos.
 */
export type ManagedRole = 'PROVIDER' | 'CUSTOMER';

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: ManagedRole;
  createdAt: string;
  // Pendiente de backend: mientras no exista la columna se asume activo (hoy todos pueden iniciar sesión)
  active?: boolean;
}

export type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

export interface UserQuery {
  role: ManagedRole;
  q?: string;
  status?: StatusFilter;
  page: number; // base 0, como Spring Data
  size: number;
}

/** Forma de PagedModel en Spring Boot (@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)). */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface RoleStats {
  total: number;
  newThisMonth: number;
  // Pendiente de backend (requieren la columna app_users.active)
  active?: number;
  inactive?: number;
}

export interface UserStats {
  providers: RoleStats;
  customers: RoleStats;
}

export interface CreateUserRequest { name: string; email: string; password: string; role: ManagedRole; }
export interface UpdateUserRequest { name: string; email: string; role: ManagedRole; }

export const isActive = (u: AdminUser) => u.active !== false;

export const ROLE_LABELS: Record<ManagedRole, { one: string; title: string; many: string; path: string }> = {
  PROVIDER: { one: 'proveedor', title: 'Proveedor', many: 'Proveedores', path: 'proveedores' },
  CUSTOMER: { one: 'cliente', title: 'Cliente', many: 'Clientes', path: 'clientes' }
};
