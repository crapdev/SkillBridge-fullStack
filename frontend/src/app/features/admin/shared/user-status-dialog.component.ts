import { Component, ElementRef, computed, effect, input, output, viewChild } from '@angular/core';
import { AvatarComponent } from './avatar.component';
import { AdminUser, ROLE_LABELS, STATUS_LABELS, StatusAction } from '../data/admin-user.model';

interface ActionCopy {
  title: (name: string) => string;
  eyebrow: string;
  text: (role: string) => string;
  button: string;
  /** Acciones que bloquean el acceso se muestran en rojo */
  danger: boolean;
  note?: string;
}

const COPY: Record<StatusAction, ActionCopy> = {
  approve: {
    title: name => `¿Aprobar a ${name}?`,
    eyebrow: 'Solicitud de proveedor',
    text: () => 'Podrá iniciar sesión, publicar sus mentorías y recibir reservas de los clientes.',
    button: 'Aprobar',
    danger: false
  },
  reject: {
    title: name => `¿Rechazar la solicitud de ${name}?`,
    eyebrow: 'Solicitud de proveedor',
    text: () => 'No podrá iniciar sesión. Si cambias de opinión, puedes aprobarlo después desde el filtro de inactivos.',
    button: 'Rechazar',
    danger: true
  },
  deactivate: {
    title: name => `¿Desactivar a ${name}?`,
    eyebrow: 'Acción administrativa reversible',
    text: role => `El ${role} no podrá iniciar sesión hasta que lo reactives. Su historial se conserva.`,
    button: 'Desactivar',
    danger: true,
    note: 'Las reservas existentes mantendrán su trazabilidad intacta.'
  },
  activate: {
    title: name => `¿Reactivar a ${name}?`,
    eyebrow: 'Acción administrativa reversible',
    text: role => `El ${role} podrá volver a iniciar sesión con sus credenciales actuales.`,
    button: 'Reactivar',
    danger: false
  }
};

/**
 * Confirmación para aprobar o rechazar proveedores y para desactivar o reactivar cuentas.
 * Los usuarios nunca se eliminan: cambiar el estado conserva su historial (reservas, servicios).
 * Usa <dialog> nativo: atrapa el foco y se cierra con Escape.
 */
@Component({
  selector: 'adm-user-status-dialog',
  standalone: true,
  imports: [AvatarComponent],
  template: `
    <dialog #dialog class="dlg" (cancel)="onCancel($event)" (click)="onBackdrop($event)" aria-labelledby="dlg-title">
      @if (user(); as u) {
        @let c = copy();
        <div class="dlg__body">
          <header class="dlg__head">
            <span class="dlg__icon" [class.ok]="!c.danger" aria-hidden="true">
              @if (action() === 'approve') {
                <svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg>
              } @else if (c.danger) {
                <svg viewBox="0 0 24 24"><path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>
              } @else {
                <svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8m0-5v5h5"/></svg>
              }
            </span>
            <div>
              <h2 id="dlg-title">{{ c.title(u.name) }}</h2>
              <p class="dlg__eyebrow" [class.ok]="!c.danger">{{ c.eyebrow }}</p>
            </div>
          </header>

          <p class="dlg__text">{{ c.text(roleLabel()) }}</p>

          <section class="dlg__summary">
            <div class="dlg__summary-head">
              <span>Resumen de cuenta</span>
              <span class="adm-chip" [class]="'adm-chip ' + statusLabel().chip">{{ statusLabel().text }}</span>
            </div>
            <div class="dlg__person">
              <adm-avatar [name]="u.name" [size]="36" />
              <div><strong>{{ u.name }}</strong><small>{{ u.email }}</small></div>
            </div>
            <div class="dlg__row"><span>Rol asignado</span><strong>{{ roleTitle() }}</strong></div>
          </section>

          @if (c.note) {
            <p class="dlg__note">{{ c.note }}</p>
          }

          <footer class="dlg__actions">
            <button type="button" class="adm-btn adm-btn--ghost" (click)="cancel.emit()" [disabled]="busy()">Cancelar</button>
            <button type="button" class="adm-btn" [class.adm-btn--danger]="c.danger" [class.adm-btn--primary]="!c.danger"
                    (click)="confirm.emit()" [disabled]="busy()">
              {{ busy() ? 'Guardando…' : c.button }}
            </button>
          </footer>
        </div>
      }
    </dialog>
  `,
  styleUrls: ['./user-status-dialog.component.css']
})
export class UserStatusDialogComponent {
  readonly user = input<AdminUser | null>(null);
  readonly action = input<StatusAction>('deactivate');
  readonly busy = input(false);
  readonly confirm = output<void>();
  readonly cancel = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly copy = computed(() => COPY[this.action()]);
  protected readonly statusLabel = computed(() => STATUS_LABELS[this.user()?.status ?? 'ACTIVE']);
  protected readonly roleLabel = computed(() => { const u = this.user(); return u ? ROLE_LABELS[u.role].one : ''; });
  protected readonly roleTitle = computed(() => { const u = this.user(); return u ? ROLE_LABELS[u.role].title : ''; });

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (this.user() && !el.open) el.showModal();
      if (!this.user() && el.open) el.close();
    });
  }

  protected onCancel(event: Event) {
    event.preventDefault();
    if (!this.busy()) this.cancel.emit();
  }

  // Clic fuera del contenido (sobre el fondo del <dialog>) cierra el modal
  protected onBackdrop(event: MouseEvent) {
    if (event.target === this.dialog().nativeElement && !this.busy()) this.cancel.emit();
  }
}
