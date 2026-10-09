import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; kind: 'success' | 'error'; text: string; }

/** Avisos breves del panel (se muestran en el layout y desaparecen solos). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  success(text: string) { this.push('success', text); }
  error(text: string) { this.push('error', text); }
  dismiss(id: number) { this.toasts.update(list => list.filter(t => t.id !== id)); }

  private push(kind: Toast['kind'], text: string) {
    const id = ++this.seq;
    this.toasts.update(list => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), 4500);
  }
}
