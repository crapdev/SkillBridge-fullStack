import { Component, inject, input, output, signal } from '@angular/core';
import { Review, ReviewService } from '../../core/review.service';

/** Largo máximo de cada comentario (el mismo que valida el backend) */
export const MAX_COMMENT = 500;
const STAR_LABELS = ['Muy mala', 'Mala', 'Regular', 'Buena', 'Excelente'];

/**
 * HU-27: formulario para calificar una reserva completada. Pide estrellas (1 a 5) y comentario opcional
 * para la mentoría y para el mentor, y envía todo junto: una reserva se califica una sola vez.
 */
@Component({
  selector: 'app-review-form',
  standalone: true,
  template: `
    <form class="rf" (submit)="$event.preventDefault(); send()" novalidate>
      <p class="rf__intro">Tu opinión ayuda a otros clientes a elegir. Solo puedes calificar una vez.</p>

      @for (part of parts; track part.key) {
        <fieldset class="rf__part">
          <legend>{{ part.legend }}</legend>
          <div class="rf__stars" role="radiogroup" [attr.aria-label]="part.legend">
            @for (n of stars; track n) {
              <button type="button" class="rf__star" role="radio"
                      [class.on]="n <= (hover()[part.key] || value(part.key))"
                      [attr.aria-checked]="value(part.key) === n"
                      [attr.aria-label]="n + (n === 1 ? ' estrella' : ' estrellas') + ': ' + starLabel(n)"
                      (click)="setValue(part.key, n)"
                      (mouseenter)="setHover(part.key, n)" (mouseleave)="setHover(part.key, 0)">★</button>
            }
            <span class="rf__hint">{{ value(part.key) ? starLabel(value(part.key)) : 'Elige de 1 a 5' }}</span>
          </div>
          <label class="rf__comment">
            <span class="visually-hidden">{{ part.commentLabel }}</span>
            <textarea rows="2" [maxLength]="maxComment" [placeholder]="part.placeholder"
                      [value]="comment(part.key)" (input)="setComment(part.key, $any($event.target).value)"></textarea>
            <small>{{ comment(part.key).length }}/{{ maxComment }}</small>
          </label>
        </fieldset>
      }

      @if (error()) { <p class="rf__error" role="alert">{{ error() }}</p> }

      <div class="rf__actions">
        <button type="button" class="rf__cancel" (click)="cancelled.emit()" [disabled]="saving()">Cancelar</button>
        <button type="submit" class="rf__send" [disabled]="saving()">
          {{ saving() ? 'Enviando…' : 'Enviar calificación' }}
        </button>
      </div>
    </form>
  `,
  styles: [`
    .rf { display: grid; gap: 14px; padding: 16px; border-radius: 12px; background: #f8fafc; border: 1px solid #e2e8f0; }
    .rf__intro { margin: 0; color: #64748b; font-size: 0.85rem; }
    .rf__part { margin: 0; padding: 0; border: 0; display: grid; gap: 6px; }
    .rf__part legend { padding: 0; margin-bottom: 4px; font-weight: 700; color: #0f172a; font-size: 0.92rem; }
    .rf__stars { display: flex; align-items: center; gap: 2px; }
    .rf__star { padding: 0 2px; border: 0; background: none; font-size: 1.6rem; line-height: 1; color: #cbd5e1;
                cursor: pointer; transition: transform 0.12s ease, color 0.12s ease; }
    .rf__star.on { color: #f59e0b; }
    .rf__star:hover { transform: scale(1.12); }
    .rf__star:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; border-radius: 4px; }
    .rf__hint { margin-left: 8px; font-size: 0.8rem; color: #64748b; }
    .rf__comment { display: grid; gap: 2px; }
    .rf__comment textarea { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #cbd5e1; border-radius: 8px;
                            font: inherit; font-size: 0.88rem; resize: vertical; background: #fff; }
    .rf__comment textarea:focus { outline: 2px solid #2563eb; outline-offset: 0; border-color: transparent; }
    .rf__comment small { justify-self: end; color: #94a3b8; font-size: 0.72rem; }
    .rf__error { margin: 0; color: #b91c1c; font-size: 0.85rem; }
    .rf__actions { display: flex; justify-content: flex-end; gap: 8px; }
    .rf__cancel, .rf__send { padding: 8px 14px; border-radius: 8px; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
    .rf__cancel { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
    .rf__send { border: 0; background: #2563eb; color: #fff; box-shadow: 0 6px 16px rgba(37, 99, 235, 0.25); }
    .rf__send:hover:not(:disabled) { background: #1d4ed8; }
    .rf__cancel:disabled, .rf__send:disabled { opacity: 0.6; cursor: wait; }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `]
})
export class ReviewFormComponent {
  private readonly reviews = inject(ReviewService);

  readonly bookingId = input.required<string>();
  readonly saved = output<Review>();
  readonly cancelled = output<void>();
  /** 409: la reserva ya tenía calificación (por ejemplo, desde otra pestaña) */
  readonly alreadyRated = output<void>();

  readonly stars = [1, 2, 3, 4, 5];
  readonly maxComment = MAX_COMMENT;
  readonly parts = [
    { key: 'session', legend: 'La mentoría', commentLabel: 'Comentario sobre la mentoría',
      placeholder: '¿Qué te pareció la sesión? (opcional)' },
    { key: 'mentor', legend: 'El mentor', commentLabel: 'Comentario sobre el mentor',
      placeholder: '¿Cómo fue el mentor? (opcional)' }
  ] as const;

  private readonly values = signal<Record<Part, number>>({ session: 0, mentor: 0 });
  private readonly comments = signal<Record<Part, string>>({ session: '', mentor: '' });
  readonly hover = signal<Record<Part, number>>({ session: 0, mentor: 0 });
  readonly saving = signal(false);
  readonly error = signal('');

  value(part: Part): number { return this.values()[part]; }
  comment(part: Part): string { return this.comments()[part]; }
  starLabel(n: number): string { return STAR_LABELS[n - 1]; }

  setValue(part: Part, n: number): void { this.values.update(v => ({ ...v, [part]: n })); this.error.set(''); }
  setHover(part: Part, n: number): void { this.hover.update(v => ({ ...v, [part]: n })); }
  setComment(part: Part, text: string): void { this.comments.update(c => ({ ...c, [part]: text })); }

  send(): void {
    const { session, mentor } = this.values();
    if (!session || !mentor) {
      this.error.set('Califica la mentoría y al mentor con al menos una estrella.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const text = (s: string) => s.trim() || null;
    this.reviews.rate(this.bookingId(), {
      rating: session,
      comment: text(this.comments().session),
      mentorRating: mentor,
      mentorComment: text(this.comments().mentor)
    }).subscribe({
      next: review => { this.saving.set(false); this.saved.emit(review); },
      error: e => {
        this.saving.set(false);
        if (e?.status === 409) { this.alreadyRated.emit(); return; }
        this.error.set(e?.error?.detail || 'No se pudo enviar tu calificación. Inténtalo de nuevo.');
      }
    });
  }
}

type Part = 'session' | 'mentor';
