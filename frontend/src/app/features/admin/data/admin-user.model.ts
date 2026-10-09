/**
 * Contrato que el frontend espera de /api/admin/users (ver docs/admin/README.md).
 */
export type ManagedRole = 'PROVIDER' | 'CUSTOMER';

/** Estado de la cuenta en app_users.status */
export type AccountStatus = 'ACTIVE' | 'PENDING_APPROVAL' | 'REJECTED' | 'INACTIVE';

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: ManagedRole;
  status: AccountStatus;
  /** Atajo de status === 'ACTIVE': la cuenta puede iniciar sesión */
  active: boolean;
  createdAt: string;
}

/** Filtros del listado; INACTIVE agrupa las cuentas desactivadas y las solicitudes rechazadas */
export type StatusFilter = 'ALL' | 'PENDING' | 'ACTIVE' | 'INACTIVE';

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
  active: number;
  /** Proveedores esperando aprobación */
  pending: number;
  /** Desactivados más solicitudes rechazadas */
  inactive: number;
}

export interface UserStats {
  providers: RoleStats;
  customers: RoleStats;
}

export interface CreateUserRequest { name: string; email: string; password: string; role: ManagedRole; }
export interface UpdateUserRequest { name: string; email: string; role: ManagedRole; }

export const isActive = (u: AdminUser) => u.status === 'ACTIVE';
export const isPending = (u: AdminUser) => u.status === 'PENDING_APPROVAL';
/** Cuenta que no puede iniciar sesión por decisión del administrador (se muestra atenuada) */
export const isBlocked = (u: AdminUser) => u.status === 'INACTIVE' || u.status === 'REJECTED';

export const STATUS_LABELS: Record<AccountStatus, { text: string; chip: string }> = {
  ACTIVE: { text: 'Activo', chip: 'adm-chip--ok' },
  PENDING_APPROVAL: { text: 'Pendiente', chip: 'adm-chip--warn' },
  REJECTED: { text: 'Rechazado', chip: 'adm-chip--danger' },
  INACTIVE: { text: 'Inactivo', chip: 'adm-chip--off' }
};

/** Acción que el administrador puede ejecutar sobre una cuenta y el estado al que la lleva */
export type StatusAction = 'approve' | 'reject' | 'deactivate' | 'activate';

export const ACTION_TARGET: Record<StatusAction, AccountStatus> = {
  approve: 'ACTIVE',
  reject: 'REJECTED',
  deactivate: 'INACTIVE',
  activate: 'ACTIVE'
};

/** Acciones disponibles según el estado actual (las mismas transiciones que valida el backend) */
export function availableActions(u: AdminUser): StatusAction[] {
  switch (u.status) {
    case 'PENDING_APPROVAL': return ['approve', 'reject'];
    case 'ACTIVE': return ['deactivate'];
    case 'REJECTED': return ['approve'];
    case 'INACTIVE': return ['activate'];
  }
}

export const ROLE_LABELS: Record<ManagedRole, { one: string; title: string; many: string; path: string }> = {
  PROVIDER: { one: 'proveedor', title: 'Proveedor', many: 'Proveedores', path: 'proveedores' },
  CUSTOMER: { one: 'cliente', title: 'Cliente', many: 'Clientes', path: 'clientes' }
};
