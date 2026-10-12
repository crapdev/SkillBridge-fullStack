import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Offering, OfferingService } from '../../core/offering.service';
import { RevealDirective } from '../../shared/reveal.directive';
import { ServiceCardComponent } from '../../shared/service-card/service-card.component';

const PAGE_SIZE = 6;
const SORT_OPTIONS = ['relevancia', 'precio-asc', 'precio-desc'] as const;
type SortOption = typeof SORT_OPTIONS[number];
type LoadStatus = 'loading' | 'ready' | 'error';

// Búsqueda sin distinguir mayúsculas ni tildes ("angular" encuentra "Angular", "diseno" encuentra "Diseño")
const normalize = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective, ServiceCardComponent],
  templateUrl: './services.component.html',
  styleUrls: ['./services.component.css']
})
export class ServicesComponent {
  private readonly offeringService = inject(OfferingService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly skeletons = [1, 2, 3, 4, 5, 6];
  readonly status = signal<LoadStatus>('loading');
  readonly offerings = signal<Offering[]>([]);

  // Los filtros viven en la URL (?q=&categoria=&orden=&pagina=) para poder compartir o recargar la búsqueda
  readonly query = signal(this.route.snapshot.queryParamMap.get('q') ?? '');
  readonly category = signal(this.route.snapshot.queryParamMap.get('categoria') ?? '');
  readonly sort = signal<SortOption>(this.parseSort(this.route.snapshot.queryParamMap.get('orden')));
  readonly page = signal<number>(this.parsePage(this.route.snapshot.queryParamMap.get('pagina')));

  readonly categories = computed(() => {
    const counts = new Map<string, number>();
    for (const o of this.offerings()) counts.set(o.category, (counts.get(o.category) ?? 0) + 1);
    return [...counts.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => a.name.localeCompare(b.name));
  });

  readonly filtered = computed(() => {
    const q = normalize(this.query().trim());
    const cat = this.category();
    const list = this.offerings().filter(o =>
      (!cat || o.category === cat) &&
      (!q || normalize(`${o.title} ${o.description} ${o.category}`).includes(q)));

    if (this.sort() === 'precio-asc') return [...list].sort((a, b) => a.price - b.price);
    if (this.sort() === 'precio-desc') return [...list].sort((a, b) => b.price - a.price);
    return list;
  });

  //Paginacion
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));

  readonly currentPage = computed(() => Math.min(Math.max(1, this.page()), this.totalPages()));

  readonly paginatedOfferings = computed(() => {
    const start = (this.currentPage() - 1) * PAGE_SIZE;
    return this.filtered().slice(start, start + PAGE_SIZE);
  });

  readonly pageNumbers = computed(() =>
    Array.from({ length: this.totalPages() }, (_, i) => i + 1)
  );

  readonly range = computed(() => {
    const total = this.filtered().length;
    if (total === 0) return { from: 0, to: 0 };
    const from = (this.currentPage() - 1) * PAGE_SIZE + 1;
    const to = Math.min(this.currentPage() * PAGE_SIZE, total);
    return { from, to };
  });

  readonly hasFilters = computed(() => !!this.query().trim() || !!this.category() || this.sort() !== 'relevancia');

  constructor() {
    this.route.queryParamMap.subscribe(params => {
      const q = params.get('q') ?? '';
      const cat = params.get('categoria') ?? '';
      const sort = this.parseSort(params.get('orden'));
      const page = this.parsePage(params.get('pagina'));

      if (this.query() !== q) this.query.set(q);
      if (this.category() !== cat) this.category.set(cat);
      if (this.sort() !== sort) this.sort.set(sort);
      if (this.page() !== page) this.page.set(page);
    });
    this.load();
  }

  load(): void {
    this.status.set('loading');
    this.offeringService.list().subscribe({
      next: list => {
        this.offerings.set(list.filter(o => o.active));
        this.status.set('ready');
      },
      error: () => this.status.set('error')
    });
  }

  setPage(p: number): void {
    if (p < 1 || p > this.totalPages() || p === this.currentPage()) return;
    this.page.set(p);
    this.syncUrl();
    window.scrollTo({ top: 320, behavior: 'smooth' });
  }

  setQuery(value: string): void { this.query.set(value); this.page.set(1); this.syncUrl(); }
  setCategory(value: string): void { this.category.set(value); this.page.set(1); this.syncUrl(); }
  setSort(value: string): void { this.sort.set(this.parseSort(value)); this.page.set(1); this.syncUrl(); }

  clearFilters(): void {
    this.query.set('');
    this.category.set('');
    this.sort.set('relevancia');
    this.page.set(1);
    this.syncUrl();
  }

  private parseSort(value: string | null): SortOption {
    return SORT_OPTIONS.includes(value as SortOption) ? value as SortOption : 'relevancia';
  }

  private parsePage(value: string | null): number {
    const parsed = parseInt(value ?? '1', 10);
    return isNaN(parsed) || parsed < 1 ? 1 : parsed;
  }

  private syncUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        q: this.query().trim() || null,
        categoria: this.category() || null,
        orden: this.sort() === 'relevancia' ? null : this.sort(),
        pagina: this.currentPage() > 1 ? this.currentPage() : null
      }
    });
  }
}
