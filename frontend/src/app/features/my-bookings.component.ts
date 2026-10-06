import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { BookingService, Booking } from '../core/booking.service';

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
                    {{ booking.status }}
                  </div>
                  <h3>Reserva ID: {{ booking.id }}</h3>
                  <p class="muted">Fecha programada: {{ booking.scheduledAt | date:'medium' }}</p>
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
    `.badge-status { display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; border-radius: 20px; background: #e2e8f0; margin-bottom: 8px; }`,
    `.badge-status.confirmed { background: #d4edda; color: #155724; }`,
    `.badge-status.created { background: #cce5ff; color: #004085; }`,
    `.badge-status.cancelled { background: #f8d7da; color: #721c24; }`,
    `.badge-status.completed { background: #e2e3e5; color: #383d41; }`
  ]
})
export class MyBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);

  bookings: Booking[] = [];
  isLoading = true;
  hasError = false;
  errorMessage = '';

  ngOnInit(): void {
    this.loadBookings();
  }

  loadBookings(): void {
    this.isLoading = true;
    this.hasError = false;
    this.errorMessage = '';

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings = data;
        this.isLoading = false;
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