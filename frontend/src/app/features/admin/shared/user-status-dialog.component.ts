import { Component, ElementRef, computed, effect, input, output, viewChild } from '@angular/core';
import { AvatarComponent } from './avatar.component';
import { AdminUser, ROLE_LABELS, isActive } from '../data/admin-user.model';

/**
 * Confirmación para desactivar o reactivar una cuenta. Los usuarios nunca se eliminan:
 * desactivar conserva su historial (reservas, servicios) y solo les impide iniciar sesión.
 * Usa <dialog> nativo: atrapa el foco y se cierra con Escape.
 */
@Component({
  selector: 'adm-user-status-dialog',
  standalone: true,
  imports: [AvatarComponent],
  template: `
    <dialog #dialog class="dlg" (cancel)="onCancel($event)" (click)="onBackdrop($event)" aria-labelledby="dlg-title">
      @if (user(); as u) {
        <div class="dlg__body">
          <header class="dlg__head">
            <span class="dlg__icon" [class.ok]="!deactivating()" aria-hidden="true">
              @if (deactivating()) {
                <svg viewBox="0 0 24 24"><path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>
              } @else {
                <svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8m0-5v5h5"/></svg>
              }
            </span>
            <div>
              <h2 id="dlg-title">{{ deactivating() ? '¿Desactivar a ' : '¿Reactivar a ' }}{{ u.name }}?</h2>
              <p class="dlg__eyebrow" [class.ok]="!deactivating()">Acción administrativa reversible</p>
            </div>
          </header>

          <p class="dlg__text">
            @if (deactivating()) {
              El {{ roleLabel() }} no podrá iniciar sesión hasta que lo reactives. Su historial se conserva.
            } @else {
              El {{ roleLabel() }} podrá volver a iniciar sesión con sus credenciales actuales.
            }
          </p>

          <section class="dlg__summary">
            <div class="dlg__summary-head">
              <span>Resumen de cuenta</span>
              <span class="adm-chip" [class.adm-chip--ok]="active()" [class.adm-chip--off]="!active()">
                {{ active() ? 'Activo' : 'Inactivo' }}
              </span>
            </div>
            <div class="dlg__person">
              <adm-avatar [name]="u.name" [size]="36" />
              <div><strong>{{ u.name }}</strong><small>{{ u.email }}</small></div>
            </div>
            <div class="dlg__row"><span>Rol asignado</span><strong>{{ roleTitle() }}</strong></div>
          </section>

          @if (deactivating()) {
            <p class="dlg__note">Las reservas existentes mantendrán su trazabilidad intacta.</p>
          }

          <footer class="dlg__actions">
            <button type="button" class="adm-btn adm-btn--ghost" (click)="cancel.emit()" [disabled]="busy()">Cancelar</button>
            <button type="button" class="adm-btn" [class.adm-btn--danger]="deactivating()" [class.adm-btn--primary]="!deactivating()"
                    (click)="confirm.emit()" [disabled]="busy()">
              {{ busy() ? 'Guardando…' : deactivating() ? 'Desactivar' : 'Reactivar' }}
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
  readonly busy = input(false);
  readonly confirm = output<void>();
  readonly cancel = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly active = computed(() => { const u = this.user(); return !!u && isActive(u); });
  protected readonly deactivating = this.active;
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
