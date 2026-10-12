import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, filter, merge, switchMap, timer } from 'rxjs';
import { AppNotification, NotificationService, NotificationType } from '../../core/notification.service';

// El contador se actualiza cada minuto mientras la pestaña está visible
const REFRESH_MS = 60_000;

const TYPE_INFO: Record<NotificationType, { tone: string; label: string }> = {
  BOOKING_CREATED: { tone: 'info', label: 'Nueva reserva' },
  BOOKING_CANCELLED: { tone: 'danger', label: 'Reserva cancelada' },
  BOOKING_PAID: { tone: 'success', label: 'Reserva pagada' }
};

const relativeFormat = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

/**
 * Campana del header del proveedor (HU-11): le avisa de lo que hacen los clientes con sus reservas.
 * El cliente no la tiene porque cada acción suya ya muestra el resultado en pantalla.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.css']
})
export class NotificationBellComponent {
  private readonly notifications = inject(NotificationService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly refresh$ = new Subject<void>();

  readonly open = signal(false);
  readonly unread = signal(0);
  readonly items = signal<AppNotification[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');

  readonly badge = computed(() => (this.unread() > 99 ? '99+' : String(this.unread())));
  readonly target = '/provider/offerings';

  constructor() {
    const navigations = inject(Router).events.pipe(filter(e => e instanceof NavigationEnd));
    merge(timer(0, REFRESH_MS), navigations, this.refresh$)
      .pipe(
        filter(() => !document.hidden),
        // Un fallo puntual no debe mostrar errores cada minuto; el siguiente ciclo lo reintenta
        switchMap(() => this.notifications.unreadCount().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed()
      )
      .subscribe(count => this.unread.set(count));
  }

  toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.open.set(true);
    this.status.set('loading');
    this.notifications.list(10).subscribe({
      next: items => {
        this.items.set(items);
        this.status.set('ready');
        // Se marcan en el servidor, pero las que llegaron nuevas siguen resaltadas mientras el panel está abierto
        if (this.unread() > 0) {
          this.notifications.markAllRead().subscribe({ next: () => this.unread.set(0), error: () => {} });
        }
      },
      error: () => this.status.set('error')
    });
  }

  close(): void { this.open.set(false); }

  // Al volver a la pestaña se actualiza enseguida en lugar de esperar al siguiente minuto
  @HostListener('document:visibilitychange')
  onVisibilityChange(): void { if (!document.hidden) this.refresh$.next(); }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.close();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.close(); }

  tone(n: AppNotification): string { return TYPE_INFO[n.type].tone; }
  label(n: AppNotification): string { return TYPE_INFO[n.type].label; }

  relative(n: AppNotification): string {
    const minutes = Math.round((new Date(n.createdAt).getTime() - Date.now()) / 60_000);
    if (Math.abs(minutes) < 1) return 'ahora';
    if (Math.abs(minutes) < 60) return relativeFormat.format(minutes, 'minute');
    const hours = Math.round(minutes / 60);
    if (Math.abs(hours) < 24) return relativeFormat.format(hours, 'hour');
    return relativeFormat.format(Math.round(hours / 24), 'day');
  }
}
