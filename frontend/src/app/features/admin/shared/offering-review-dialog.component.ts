import { Component, ElementRef, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { AvatarComponent } from './avatar.component';
import { AdminOffering, MAX_REJECTION_REASON, ReviewAction } from '../data/admin-offering.model';
import { formatDuration, formatPrice, levelLabel } from '../../../shared/offering-format';

/**
 * Confirmación para aprobar o rechazar una mentoría (HU-21). Al rechazar se puede escribir un motivo
 * opcional que el proveedor verá en su panel. Usa <dialog> nativo: atrapa el foco y se cierra con Escape.
 */
@Component({
  selector: 'adm-offering-review-dialog',
  standalone: true,
  imports: [AvatarComponent],
  template: `
    <dialog #dialog class="dlg" (cancel)="onCancel($event)" (click)="onBackdrop($event)" aria-labelledby="review-title">
      @if (offering(); as o) {
        <div class="dlg__body">
          <header class="dlg__head">
            <span class="dlg__icon" [class.ok]="approving()" aria-hidden="true">
              @if (approving()) {
                <svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg>
              } @else {
                <svg viewBox="0 0 24 24"><path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>
              }
            </span>
            <div>
              <h2 id="review-title">{{ approving() ? '¿Aprobar' : '¿Rechazar' }} «{{ o.title }}»?</h2>
              <p class="dlg__eyebrow" [class.ok]="approving()">Revisión de mentoría</p>
            </div>
          </header>

          <p class="dlg__text">
            @if (approving()) {
              Aparecerá en el catálogo en cuanto tenga horarios publicados, y los clientes podrán reservarla.
            } @else {
              No será visible para los clientes. El proveedor verá el estado y podrá corregirla para enviarla de nuevo a revisión.
            }
          </p>

          <section class="dlg__summary">
            <div class="dlg__summary-head"><span>Mentoría</span><span class="adm-chip adm-chip--warn">En revisión</span></div>
            <div class="dlg__person">
              <adm-avatar [name]="o.providerName || 'Proveedor'" [size]="36" />
              <div><strong>{{ o.providerName || 'Proveedor' }}</strong><small>{{ o.providerEmail }}</small></div>
            </div>
            <div class="dlg__row"><span>Categoría</span><strong>{{ o.category }}</strong></div>
            <div class="dlg__row"><span>Nivel · duración</span><strong>{{ level(o) }} · {{ duration(o) }}</strong></div>
            <div class="dlg__row"><span>Precio por sesión</span><strong>{{ price(o) }}</strong></div>
          </section>

          @if (!approving()) {
            <label class="reason">
              <span class="reason__label">Motivo del rechazo <small>(opcional)</small></span>
              <textarea rows="3" [maxLength]="max" [value]="reason()" (input)="reason.set($any($event.target).value)"
                        [disabled]="busy()" placeholder="Ej.: agrega al temario un tema sobre seguridad"></textarea>
              <span class="reason__hint">Lo verá el proveedor en su panel · {{ reason().length }}/{{ max }}</span>
            </label>
          }

          <footer class="dlg__actions">
            <button type="button" class="adm-btn adm-btn--ghost" (click)="cancel.emit()" [disabled]="busy()">Cancelar</button>
            <button type="button" class="adm-btn" [class.adm-btn--danger]="!approving()" [class.adm-btn--primary]="approving()"
                    (click)="confirm.emit(reason())" [disabled]="busy()">
              {{ busy() ? 'Guardando…' : (approving() ? 'Aprobar' : 'Rechazar') }}
            </button>
          </footer>
        </div>
      }
    </dialog>
  `,
  styleUrls: ['./user-status-dialog.component.css', './offering-review-dialog.component.css']
})
export class OfferingReviewDialogComponent {
  readonly offering = input<AdminOffering | null>(null);
  readonly action = input<ReviewAction>('approve');
  readonly busy = input(false);
  /** Emite el motivo (vacío si no se escribió); al aprobar se ignora */
  readonly confirm = output<string>();
  readonly cancel = output<void>();

  protected readonly max = MAX_REJECTION_REASON;
  protected readonly reason = signal('');
  protected readonly approving = computed(() => this.action() === 'approve');
  protected readonly level = (o: AdminOffering) => levelLabel(o.level);
  protected readonly duration = (o: AdminOffering) => formatDuration(o.durationMinutes);
  protected readonly price = (o: AdminOffering) => formatPrice(o.price);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (this.offering() && !el.open) {
        // Cada revisión empieza sin el motivo de la anterior
        this.reason.set('');
        el.showModal();
      }
      if (!this.offering() && el.open) el.close();
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
