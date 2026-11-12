import { Component, computed, input } from '@angular/core';
import { formatAverage, ratingCountText } from '../../core/review.service';

/**
 * HU-27: estrellas de solo lectura con el promedio y la cantidad de calificaciones.
 * Las estrellas doradas se recortan al porcentaje del promedio (4,5 = 90 %), así se ven medias estrellas.
 */
@Component({
  selector: 'app-rating-stars',
  standalone: true,
  template: `
    <span class="rating" [class.rating--lg]="size() === 'lg'" [class.rating--dark]="tone() === 'dark'">
      @if (hasRatings()) {
        <span class="stars" role="img" [attr.aria-label]="label()">
          <span class="stars__base" aria-hidden="true">★★★★★</span>
          <span class="stars__fill" aria-hidden="true" [style.width.%]="percent()">★★★★★</span>
        </span>
        <strong class="rating__value">{{ averageText() }}</strong>
        @if (showCount()) { <span class="rating__count">({{ countText() }})</span> }
      } @else {
        <span class="rating__empty">{{ emptyText() }}</span>
      }
    </span>
  `,
  styles: [`
    :host { display: inline-flex; }
    .rating { display: inline-flex; align-items: center; gap: 6px; font-size: 0.85rem; color: #475569; }
    .rating--lg { font-size: 1rem; gap: 8px; }
    .rating--dark { color: #cbd5e1; }
    .stars { position: relative; display: inline-block; line-height: 1; letter-spacing: 1px; }
    .stars__base { color: #e2e8f0; }
    .rating--dark .stars__base { color: rgba(255, 255, 255, 0.22); }
    .stars__fill { position: absolute; inset: 0 auto 0 0; overflow: hidden; white-space: nowrap; color: #f59e0b; }
    .rating__value { color: #0f172a; font-weight: 700; }
    .rating--dark .rating__value { color: #fff; }
    .rating__count { color: inherit; }
    .rating__empty { font-style: italic; color: #94a3b8; }
  `]
})
export class RatingStarsComponent {
  readonly average = input<number | null>(null);
  readonly count = input(0);
  readonly showCount = input(true);
  readonly size = input<'sm' | 'lg'>('sm');
  readonly tone = input<'light' | 'dark'>('light');
  readonly emptyText = input('Sin calificaciones');

  readonly hasRatings = computed(() => this.count() > 0 && this.average() !== null);
  readonly percent = computed(() => Math.max(0, Math.min(100, ((this.average() ?? 0) / 5) * 100)));
  readonly averageText = computed(() => formatAverage(this.average()));
  readonly countText = computed(() => ratingCountText(this.count()));
  readonly label = computed(() => `${this.averageText()} de 5 estrellas`);
}
