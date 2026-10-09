import { Component, computed, input } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Offering } from '../../core/offering.service';
import { SERVICE_CONTENT, categoryKey } from '../../features/service-detail/service-content';

/** Tarjeta de un servicio del catálogo; la usan el inicio y la página de servicios. */
@Component({
  selector: 'app-service-card',
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  template: `
    <article class="service-card">
      <div class="card-top">
        <span class="category" [class]="'category ' + category()">{{ offering().category }}</span>
        <div class="service-icon" [class]="'service-icon ' + category()">
          <img [src]="icon()" [alt]="'Icono ' + offering().category">
        </div>
      </div>

      <div class="card-content">
        <h3><a [routerLink]="['/services', offering().id]">{{ offering().title }}</a></h3>
        <p class="description">{{ offering().description }}</p>
      </div>

      <div class="card-footer">
        <div class="price-container">
          <span class="price-label">INVERSIÓN</span>
          <div class="price">
            {{ offering().price | currency:'COP':'symbol-narrow':'1.0-0' }}
            <span>/ sesión</span>
          </div>
        </div>
        <a class="reserve-button" [routerLink]="['/services', offering().id]">
          Ver detalle <span aria-hidden="true">→</span>
        </a>
      </div>
    </article>
  `,
  styleUrls: ['./service-card.component.css']
})
export class ServiceCardComponent {
  readonly offering = input.required<Offering>();

  readonly category = computed(() => categoryKey(this.offering().category));
  readonly icon = computed(() => SERVICE_CONTENT[this.category()].icon);
}
