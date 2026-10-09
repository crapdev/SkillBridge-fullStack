import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { AdminUsersService } from '../data/admin-users.service';
import { AdminUser, ManagedRole, Page, ROLE_LABELS, RoleStats, StatusFilter, isActive } from '../data/admin-user.model';
import { AvatarComponent } from '../shared/avatar.component';
import { UserStatusDialogComponent } from '../shared/user-status-dialog.component';
import { ToastService } from '../shared/toast.service';
import { errorMessage, formatDate } from '../shared/format';
import { userStatusEnabled } from '../admin.config';

const PAGE_SIZE = 10;

/** Listado paginado de proveedores o clientes; el rol llega en `data.role` de la ruta. */
@Component({
  standalone: true,
  imports: [RouterLink, AvatarComponent, UserStatusDialogComponent],
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.css']
})
export class UserListComponent {
  private service = inject(AdminUsersService);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  protected readonly role: ManagedRole = inject(ActivatedRoute).snapshot.data['role'];
  protected readonly label = ROLE_LABELS[this.role];
  protected readonly statusEnabled = userStatusEnabled();
  protected readonly formatDate = formatDate;
  protected readonly isActive = isActive;
  protected readonly filters: { value: StatusFilter; text: string }[] = [
    { value: 'ALL', text: 'Todos' }, { value: 'ACTIVE', text: 'Activos' }, { value: 'INACTIVE', text: 'Inactivos' }
  ];

  protected readonly q = signal('');
  protected readonly status = signal<StatusFilter>('ALL');
  protected readonly pageIndex = signal(0);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly page = signal<Page<AdminUser> | null>(null);
  protected readonly stats = signal<RoleStats | null>(null);

  protected readonly selected = signal<AdminUser | null>(null);
  protected readonly saving = signal(false);

  protected readonly rows = computed(() => this.page()?.content ?? []);
  protected readonly total = computed(() => this.page()?.page.totalElements ?? 0);
  protected readonly totalPages = computed(() => this.page()?.page.totalPages ?? 0);
  protected readonly range = computed(() => {
    const start = this.pageIndex() * PAGE_SIZE;
    return { from: this.total() ? start + 1 : 0, to: start + this.rows().length };
  });
  // Ventana de hasta 5 números alrededor de la página actual
  protected readonly pageNumbers = computed(() => {
    const count = this.totalPages();
    const first = Math.max(0, Math.min(this.pageIndex() - 2, count - 5));
    return Array.from({ length: Math.min(5, count) }, (_, i) => first + i);
  });

  private readonly search$ = new Subject<string>();
  private readonly reload$ = new Subject<void>();

  constructor() {
    this.search$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(value => { this.q.set(value); this.goTo(0); });

    // switchMap descarta respuestas viejas si el usuario cambia de página o filtro antes de que lleguen
    this.reload$.pipe(
      switchMap(() => this.service.list({ role: this.role, q: this.q(), status: this.status(), page: this.pageIndex(), size: PAGE_SIZE })
        .pipe(map(page => ({ page, error: '' })), catchError(e => of({ page: null, error: errorMessage(e, `No fue posible cargar los ${this.label.many.toLowerCase()}.`) })))),
      takeUntilDestroyed()
    ).subscribe(({ page, error }) => {
      if (page) this.page.set(page);
      this.error.set(error);
      this.loading.set(false);
    });

    this.load();
    this.loadStats();
  }

  protected onSearch(value: string) { this.search$.next(value); }

  protected setStatus(value: StatusFilter) {
    if (value === this.status()) return;
    this.status.set(value);
    this.goTo(0);
  }

  protected goTo(index: number) {
    this.pageIndex.set(index);
    this.load();
  }

  protected load() {
    this.loading.set(true);
    this.reload$.next();
  }

  private loadStats() {
    this.service.stats().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: s => this.stats.set(this.role === 'PROVIDER' ? s.providers : s.customers),
      error: () => this.stats.set(null)
    });
  }

  protected confirmStatus() {
    const user = this.selected();
    if (!user) return;
    const activate = !isActive(user);
    this.saving.set(true);
    this.service.setStatus(user.id, activate).subscribe({
      next: () => {
        this.saving.set(false);
        this.selected.set(null);
        this.toast.success(`${user.name} fue ${activate ? 'reactivado' : 'desactivado'}.`);
        this.load();
        this.loadStats();
      },
      error: e => {
        this.saving.set(false);
        this.toast.error(errorMessage(e, 'No fue posible cambiar el estado de la cuenta.'));
      }
    });
  }
}
