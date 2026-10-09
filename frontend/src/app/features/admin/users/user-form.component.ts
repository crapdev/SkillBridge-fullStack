import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AdminUsersService } from '../data/admin-users.service';
import { AdminUser, ManagedRole, ROLE_LABELS } from '../data/admin-user.model';
import { ToastService } from '../shared/toast.service';
import { errorMessage } from '../shared/format';

/** Alta y edición de proveedores/clientes. Las reglas replican las validaciones del backend (RegisterRequest). */
@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './user-form.component.html',
  styleUrls: ['./user-form.component.css']
})
export class UserFormComponent {
  private service = inject(AdminUsersService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  protected readonly id = this.route.snapshot.paramMap.get('id');
  protected readonly editing = this.id !== null;
  private readonly routeRole: ManagedRole = this.route.snapshot.data['role'];
  protected readonly labels = ROLE_LABELS;
  protected readonly roles: ManagedRole[] = ['PROVIDER', 'CUSTOMER'];

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    role: [this.routeRole, Validators.required],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]]
  });

  protected readonly original = signal<AdminUser | null>(null);
  protected readonly loading = signal(this.editing);
  protected readonly loadError = signal('');
  protected readonly saving = signal(false);
  protected readonly submitError = signal('');
  protected readonly showPassword = signal(false);

  private readonly role = toSignal(this.form.controls.role.valueChanges, { initialValue: this.routeRole });
  // La miga de pan y el botón "Cancelar" vuelven al listado del que se vino
  protected readonly backLabel = ROLE_LABELS[this.routeRole];
  protected readonly roleChanged = computed(() => !!this.original() && this.original()!.role !== this.role());
  protected readonly title = computed(() => this.editing ? `Editar ${this.labels[this.routeRole].one}` : `Nuevo ${this.labels[this.role()].one}`);

  constructor() {
    if (this.editing) {
      // La contraseña no se edita desde el panel: el campo no existe en modo edición
      this.form.controls.password.disable();
      this.loadUser();
    }
  }

  protected loadUser() {
    this.loading.set(true);
    this.loadError.set('');
    this.service.get(this.id!).subscribe({
      next: user => {
        this.original.set(user);
        this.form.patchValue({ name: user.name, email: user.email, role: user.role });
        this.loading.set(false);
      },
      error: e => {
        this.loadError.set(errorMessage(e, 'No fue posible cargar la cuenta.'));
        this.loading.set(false);
      }
    });
  }

  protected invalid(name: 'name' | 'email' | 'password') {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected submit() {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.submitError.set('');
    const { name, email, role, password } = this.form.getRawValue();
    const request = this.editing
      ? this.service.update(this.id!, { name: name.trim(), email: email.trim(), role })
      : this.service.create({ name: name.trim(), email: email.trim(), password, role });

    request.subscribe({
      next: saved => {
        this.toast.success(this.editing ? `Se guardaron los cambios de ${saved.name}.` : `${saved.name} fue registrado como ${this.labels[saved.role].one}.`);
        this.router.navigate(['/admin', this.labels[saved.role].path]);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        // 422 = regla de negocio; hoy la única posible es el correo duplicado
        if (e instanceof HttpErrorResponse && e.status === 422) {
          this.form.controls.email.setErrors({ duplicate: e.error?.detail || 'El correo ya está registrado' });
          this.form.controls.email.markAsTouched();
          return;
        }
        this.submitError.set(errorMessage(e, 'No fue posible guardar la cuenta. Inténtalo de nuevo.'));
      }
    });
  }

  protected cancel() { this.router.navigate(['/admin', this.backLabel.path]); }
}
