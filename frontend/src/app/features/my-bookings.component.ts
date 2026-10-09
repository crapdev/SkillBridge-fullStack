import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { BookingService, Booking, BookingStatus } from '../core/booking.service';
import { OfferingService } from '../core/offering.service';
import { AlertService } from '../shared/alert.service';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, DatePipe],
  template: `
    <section class="container section">
      <div class="card my-bookings-card">
        <h1>Mis Reservas</h1>
        <p class="muted">Consulta el estado y fecha de tus sesiones reservadas.</p>

        <!-- Estado de Carga -->
        @if (isLoading) {
          <div class="state-box loading">
            <p>Cargando tus reservas...</p>
          </div>
        }

        <!-- Estado de Error -->
        @if (!isLoading && hasError) {
          <div class="state-box error-box">
            <p>{{ errorMessage }}</p>
            <button class="btn secondary" (click)="loadBookings()" style="margin-top: 10px;">Reintentar</button>
          </div>
        }

        <!-- Contenedor principal cuando no hay errores ni carga -->
        @if (!isLoading && !hasError) {
          
          <!-- Estado de Lista Vacía (200 OK pero sin registros) -->
          @if (bookings.length === 0) {
            <div class="state-box empty-box">
              <p>No tienes reservas registradas en este momento.</p>
            </div>
          }

          <!-- Estado Exitoso: Lista con Reservas -->
          @if (bookings.length > 0) {
            <div class="grid" style="margin-top: 20px;">
              @for (booking of bookings; track booking.id) {
                <article class="card booking-item">
                  <div class="badge-status {{ booking.status.toLowerCase() }}">
                    {{ statusLabel(booking.status) }}
                  </div>
                  @if (booking.status === 'CREATED') {
                    <p class="booking-note">Esperando confirmación</p>
                  }
                  <h3>{{ offeringTitles.get(booking.offeringId) ?? 'Servicio no disponible' }}</h3>
                  <p class="muted">Fecha programada: {{ booking.scheduledAt | date:'medium' }}</p>
                  
                  @if (isCancelable(booking)) {
                    <button class="btn secondary cancel-btn" (click)="cancel(booking.id)" [disabled]="isCanceling(booking.id)">
                      {{ isCanceling(booking.id) ? 'Cancelando...' : 'Cancelar reserva' }}
                    </button>
                  }
                </article>
              }
            </div>
          }

        }
      </div>
    </section>
  `,
  styles: [
    `.section { padding: 50px 0; }`,
    `.my-bookings-card { max-width: 800px; margin: auto; }`,
    `.state-box { padding: 24px; text-align: center; border-radius: 12px; background: #f3f6fb; margin-top: 20px; }`,
    `.empty-box { background: #fff8e1; color: #8f6b00; border: 1px solid #ffe082; }`,
    `.error-box { background: #ffebee; color: #c62828; border: 1px solid #ffcdd2; }`,
    `.booking-item { position: relative; border: 1px solid #e7ebf0; padding: 20px; border-radius: 10px; margin-bottom: 12px; }`,
    `.booking-note { color: #6b7280; margin: 0 0 12px; }`,
    `.badge-status { display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; border-radius: 20px; background: #e2e8f0; margin-bottom: 8px; }`,
    `.badge-status.confirmed { background: #d4edda; color: #155724; }`,
    `.badge-status.created { background: #cce5ff; color: #004085; }`,
    `.badge-status.cancelled { background: #f8d7da; color: #721c24; }`,
    `.badge-status.completed { background: #e2e3e5; color: #383d41; }`,
    `.cancel-btn { margin-top: 10px; font-size: 14px; padding: 6px 12px; }`
  ]
})
export class MyBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);
  private offeringService = inject(OfferingService);
  private alerts = inject(AlertService);

  bookings: Booking[] = [];
  offeringTitles = new Map<string, string>();
  isLoading = true;
  hasError = false;
  errorMessage = '';
  cancelingIds = new Set<string>();

  isCancelable(booking: Booking): boolean {
    return booking.status === 'CREATED' || booking.status === 'CONFIRMED';
  }

  isCanceling(id: string): boolean {
    return this.cancelingIds.has(id);
  }

  async cancel(id: string): Promise<void> {
    const confirmed = await this.alerts.confirm({
      title: '¿Cancelar esta reserva?',
      message: 'La sesión quedará cancelada y no podrás reactivarla.',
      confirmText: 'Sí, cancelar',
      cancelText: 'Volver',
      danger: true
    });
    if (!confirmed) {
      return;
    }

    this.cancelingIds.add(id);
    this.bookingService.cancelBooking(id).subscribe({
      next: () => {
        this.alerts.success('Reserva cancelada', 'Tu reserva se canceló correctamente.');
        this.cancelingIds.delete(id);
        this.loadBookings();
      },
      error: (error) => {
        console.error('Error al cancelar', error);
        this.cancelingIds.delete(id);
        const msg = error.error?.detail || 'Ocurrió un error al cancelar la reserva.';
        this.alerts.error('No se pudo cancelar', msg);
      }
    });
  }

  statusLabel(status: BookingStatus): string {
    const labels: Record<BookingStatus, string> = {
      CREATED: 'Creado',
      CONFIRMED: 'Confirmado',
      CANCELLED: 'Cancelado',
      COMPLETED: 'Completado'
    };
    return labels[status];
  }

  ngOnInit(): void {
    this.loadBookings();
  }

  loadBookings(): void {
    this.isLoading = true;
    this.hasError = false;
    this.errorMessage = '';
    this.offeringTitles = new Map();

    this.bookingService.getMyBookings().subscribe({
      next: (bookings) => {
        this.bookings = bookings;
        this.isLoading = false;

        if (bookings.length > 0) {
          this.offeringService.list().subscribe({
            next: (offerings) => {
              this.offeringTitles = new Map(offerings.map((offering) => [offering.id, offering.title]));
            },
            error: (error) => {
              console.error('Error al obtener los nombres de los servicios de tus reservas', error);
            }
          });
        }
      },
      error: (error) => {
        console.error('Error al obtener mis reservas', error);
        this.hasError = true;
        this.errorMessage = 'Ocurrió un error al cargar tus reservas. Intenta nuevamente.';
        this.isLoading = false;
      }
    });
  }
}