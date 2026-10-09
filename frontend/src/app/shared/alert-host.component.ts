import { Component, ElementRef, HostListener, effect, inject, viewChild } from '@angular/core';
import { AlertService } from './alert.service';

/** Contenedor global de toasts y del diálogo de confirmación. Se monta una sola vez en AppComponent. */
@Component({
  selector: 'app-alert-host',
  standalone: true,
  template: `
    @if (alerts.confirmation(); as dialog) {
      <div class="overlay" (click)="alerts.answer(false)">
        <div class="dialog" role="alertdialog" aria-modal="true"
             aria-labelledby="dialog-title" [attr.aria-describedby]="dialog.message ? 'dialog-message' : null"
             (click)="$event.stopPropagation()">
          <span class="dialog-icon" [class.danger]="dialog.danger" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M12 9v4M12 17h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" /></svg>
          </span>
          <h2 id="dialog-title">{{ dialog.title }}</h2>
          @if (dialog.message) { <p id="dialog-message">{{ dialog.message }}</p> }
          <div class="dialog-actions">
            <button #cancelBtn type="button" class="a-btn ghost" (click)="alerts.answer(false)">
              {{ dialog.cancelText ?? 'Cancelar' }}
            </button>
            <button type="button" class="a-btn" [class.danger]="dialog.danger" (click)="alerts.answer(true)">
              {{ dialog.confirmText ?? 'Aceptar' }}
            </button>
          </div>
        </div>
      </div>
    }

    <div class="toasts" aria-live="polite">
      @for (toast of alerts.toasts(); track toast.id) {
        <div class="toast {{ toast.kind }}" [attr.role]="toast.kind === 'error' ? 'alert' : 'status'">
          <span class="toast-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              @switch (toast.kind) {
                @case ('success') { <path d="M20 6 9 17l-5-5" /> }
                @case ('error') { <path d="M18 6 6 18M6 6l12 12" /> }
                @default { <path d="M12 16v-4M12 8h.01" /> }
              }
            </svg>
          </span>
          <div class="toast-body">
            <strong>{{ toast.title }}</strong>
            @if (toast.message) { <span>{{ toast.message }}</span> }
          </div>
          <button type="button" class="toast-close" aria-label="Cerrar notificación" (click)="alerts.dismiss(toast.id)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      --primary: #2563eb;
      --primary-dark: #1d4ed8;
      --accent: #38bdf8;
      --ink: #0f1b3d;
      --muted: #475467;
      --line: #e4e8f0;
      --danger: #dc2626;
      --danger-dark: #b91c1c;
      --success: #059669;
    }
    svg { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }

    .overlay {
      position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px;
      background: rgba(11, 29, 58, .55); backdrop-filter: blur(3px); animation: fade .15s ease-out;
    }
    .dialog {
      position: relative; width: 100%; max-width: 420px; padding: 28px 28px 24px; overflow: hidden;
      background: #fff; border-radius: 12px; color: var(--ink); text-align: center;
      box-shadow: 0 24px 60px rgba(11, 29, 58, .35); animation: pop .18s cubic-bezier(.2, .7, .2, 1);
    }
    /* Franja superior con el degradado de la marca */
    .dialog::before { content: ""; position: absolute; inset: 0 0 auto; height: 4px; background: linear-gradient(90deg, var(--primary), var(--accent)); }
    .dialog-icon {
      display: inline-grid; place-items: center; width: 48px; height: 48px; padding: 12px; margin-bottom: 12px;
      border-radius: 50%; background: #e8efff; color: var(--primary);
    }
    .dialog-icon.danger { background: #fef2f2; color: var(--danger); }
    h2 { margin: 0 0 8px; font-size: 1.2rem; font-weight: 800; letter-spacing: -.01em; }
    p { margin: 0; color: var(--muted); line-height: 1.5; }
    .dialog-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 24px; }

    .a-btn {
      height: 42px; padding: 0 16px; border: 0; border-radius: 8px; cursor: pointer; font-weight: 600;
      background: var(--primary); color: #fff; transition: background .15s, box-shadow .15s;
    }
    .a-btn:hover { background: var(--primary-dark); }
    .a-btn.danger { background: var(--danger); }
    .a-btn.danger:hover { background: var(--danger-dark); }
    .a-btn.ghost { background: #f4f6fb; color: var(--ink); border: 1px solid var(--line); }
    .a-btn.ghost:hover { background: #e8efff; }
    .a-btn:focus-visible, .toast-close:focus-visible { outline: none; box-shadow: 0 0 0 3px rgba(37, 99, 235, .35); }

    .toasts {
      position: fixed; z-index: 1001; top: 88px; right: 16px; display: grid; gap: 10px;
      width: min(380px, calc(100vw - 32px)); pointer-events: none;
    }
    .toast {
      display: flex; align-items: flex-start; gap: 12px; padding: 14px 14px 14px 16px; pointer-events: auto;
      background: #fff; color: var(--ink); border: 1px solid var(--line); border-left: 4px solid var(--primary);
      border-radius: 10px; box-shadow: 0 12px 32px rgba(16, 24, 40, .14); animation: slide .22s cubic-bezier(.2, .7, .2, 1);
    }
    .toast.success { border-left-color: var(--success); }
    .toast.error { border-left-color: var(--danger); }
    .toast-icon { flex: none; width: 28px; height: 28px; padding: 6px; border-radius: 50%; background: #e8efff; color: var(--primary); }
    .toast.success .toast-icon { background: #ecfdf5; color: var(--success); }
    .toast.error .toast-icon { background: #fef2f2; color: var(--danger); }
    .toast-body { flex: 1; display: grid; gap: 2px; padding-top: 3px; font-size: .93rem; }
    .toast-body span { color: var(--muted); font-size: .88rem; line-height: 1.4; }
    .toast-close {
      flex: none; width: 26px; height: 26px; padding: 6px; border: 0; border-radius: 6px;
      background: none; color: var(--muted); cursor: pointer;
    }
    .toast-close:hover { background: #f4f6fb; color: var(--ink); }

    @keyframes fade { from { opacity: 0; } }
    @keyframes pop { from { opacity: 0; transform: translateY(8px) scale(.97); } }
    @keyframes slide { from { opacity: 0; transform: translateX(24px); } }
  `]
})
export class AlertHostComponent {
  readonly alerts = inject(AlertService);
  private readonly cancelBtn = viewChild<ElementRef<HTMLButtonElement>>('cancelBtn');

  constructor() {
    // Foco en "Cancelar" al abrir: en acciones destructivas la opción segura es la predeterminada
    effect(() => this.cancelBtn()?.nativeElement.focus());
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.alerts.answer(false); }
}
