import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, of, switchMap, throwError, timer } from 'rxjs';
import { adminMockEnabled } from '../admin.config';
import { AdminUser, CreateUserRequest, ManagedRole, Page, RoleStats, UpdateUserRequest, UserStats } from './admin-user.model';

/**
 * Backend simulado de /api/admin/users para desarrollo. Implementa el mismo contrato que se le
 * pide al backend real (docs/ADMIN-PANEL.md), así las vistas no cambian al conectarlo.
 * Los datos viven en memoria y se reinician al recargar la página.
 */
export const adminMockInterceptor: HttpInterceptorFn = (req, next) => {
  const match = req.url.match(/\/admin\/users(?:\/([^/?]+))?(?:\/(status))?$/);
  if (!match || !adminMockEnabled()) return next(req);
  return handle(req, match[1], match[2]).pipe(delay(350));
};

const NAMES = [
  'Ana Pérez', 'Carlos Morales', 'Valentina Gómez', 'Javier Fernández', 'Mariana Silva', 'Esteban Rojas',
  'Daniela Castro', 'Roberto Méndez', 'Lucía Torres', 'Gabriel Ortiz', 'Mateo Morales', 'Sofía Valenzuela',
  'Alejandro Ramos', 'Camila Herrera', 'Diego Navarro', 'Isabella Duarte', 'Samuel Restrepo', 'Paula Cárdenas',
  'Andrés Quintero', 'Laura Salazar', 'Felipe Arango', 'Natalia Rincón', 'Sebastián Vargas', 'Juliana Mejía',
  'Tomás Giraldo', 'Manuela Ríos', 'Nicolás Pineda', 'Sara Londoño', 'Martín Ospina', 'Valeria Muñoz',
  'Emilio Cortés', 'Antonia Franco', 'Simón Zapata', 'Renata Acosta'
];

const DAY = 86_400_000;
const users: AdminUser[] = NAMES.map((name, i) => ({
  id: crypto.randomUUID(),
  name,
  email: name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(' ', '.') + '@skillbridge.dev',
  role: (i % 3 === 2 ? 'CUSTOMER' : 'PROVIDER') as ManagedRole,
  createdAt: new Date(Date.now() - i * i * 0.12 * DAY - i * 3_600_000).toISOString(),
  active: !(i === 4 || i === 7 || i === 17 || i === 26)
}));

function handle(req: HttpRequest<unknown>, id?: string, status?: string): Observable<HttpResponse<unknown>> {
  if (req.method === 'GET' && id === 'stats') return ok(stats());
  if (req.method === 'GET' && !id) return ok(list(req));
  if (req.method === 'POST' && !id) return create(req.body as CreateUserRequest);

  const user = users.find(u => u.id === id);
  if (!user) return fail(404, 'Usuario no encontrado');
  if (req.method === 'GET') return ok(user);
  if (req.method === 'PUT') return update(user, req.body as UpdateUserRequest);
  if (req.method === 'PATCH' && status) {
    user.active = (req.body as { active: boolean }).active;
    return ok(user);
  }
  return fail(405, 'Método no soportado');
}

function list(req: HttpRequest<unknown>): Page<AdminUser> {
  const p = req.params;
  const q = (p.get('q') ?? '').toLowerCase();
  const active = p.get('active');
  const page = Number(p.get('page') ?? 0);
  const size = Number(p.get('size') ?? 10);
  const rows = users
    .filter(u => u.role === p.get('role'))
    .filter(u => !q || u.name.toLowerCase().includes(q) || u.email.includes(q))
    .filter(u => active === null || String(u.active !== false) === active)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return {
    content: rows.slice(page * size, page * size + size),
    page: { size, number: page, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
  };
}

function stats(): UserStats {
  const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString();
  const forRole = (role: ManagedRole): RoleStats => {
    const rows = users.filter(u => u.role === role);
    const active = rows.filter(u => u.active !== false).length;
    return { total: rows.length, newThisMonth: rows.filter(u => u.createdAt >= monthStart).length, active, inactive: rows.length - active };
  };
  return { providers: forRole('PROVIDER'), customers: forRole('CUSTOMER') };
}

function create(body: CreateUserRequest) {
  const email = body.email.trim().toLowerCase();
  if (users.some(u => u.email === email)) return fail(422, 'El correo ya está registrado');
  const user: AdminUser = { id: crypto.randomUUID(), name: body.name.trim(), email, role: body.role, createdAt: new Date().toISOString(), active: true };
  users.unshift(user);
  return ok(user, 201);
}

function update(user: AdminUser, body: UpdateUserRequest) {
  const email = body.email.trim().toLowerCase();
  if (users.some(u => u.email === email && u.id !== user.id)) return fail(422, 'El correo ya está registrado');
  Object.assign(user, { name: body.name.trim(), email, role: body.role });
  return ok(user);
}

const ok = (body: unknown, status = 200) => of(new HttpResponse({ status, body }));
// Mismo formato ProblemDetail que devuelve GlobalExceptionHandler (delay() no retrasa errores, por eso timer)
const fail = (status: number, detail: string) =>
  timer(350).pipe(switchMap(() => throwError(() => new HttpErrorResponse({ status, error: { status, detail } }))));
