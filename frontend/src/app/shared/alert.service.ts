import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
}

export interface ConfirmOptions {
  title: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  /** Pinta el botón de confirmar en rojo para acciones destructivas */
  danger?: boolean;
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (accepted: boolean) => void;
}

/** Reemplaza window.alert / window.confirm con componentes propios (ver AlertHostComponent). */
@Injectable({ providedIn: 'root' })
export class AlertService {
  private nextId = 0;
  readonly toasts = signal<Toast[]>([]);
  readonly confirmation = signal<PendingConfirm | null>(null);

  success(title: string, message?: string): void { this.show('success', title, message); }
  error(title: string, message?: string): void { this.show('error', title, message, 6000); }
  info(title: string, message?: string): void { this.show('info', title, message); }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  confirm(options: ConfirmOptions): Promise<boolean> {
    // Si ya hay un diálogo abierto, se descarta como cancelado
    this.confirmation()?.resolve(false);
    return new Promise(resolve => this.confirmation.set({ ...options, resolve }));
  }

  /** Lo llama AlertHostComponent cuando el usuario responde el diálogo */
  answer(accepted: boolean): void {
    const pending = this.confirmation();
    if (!pending) return;
    this.confirmation.set(null);
    pending.resolve(accepted);
  }

  private show(kind: ToastKind, title: string, message?: string, duration = 4000): void {
    const id = ++this.nextId;
    this.toasts.update(list => [...list, { id, kind, title, message }]);
    setTimeout(() => this.dismiss(id), duration);
  }
}
