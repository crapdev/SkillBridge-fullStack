import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OfferingService, Offering } from '../core/offering.service';

@Component({
  selector: 'app-provider-offerings',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="container section">
      <div class="dashboard-header">
        <h1>Panel de Mentorías</h1>
        <p class="muted">Crea nuevos servicios y gestiona tu catálogo actual.</p>
      </div>

      <div class="dashboard-grid">
        <!-- Formulario para crear mentoría -->
        <div class="card form-card">
          <h2>Nueva Mentoría</h2>
          
          @if (successMessage) {
            <div class="alert alert-success">{{ successMessage }}</div>
          }
          @if (errorMessage) {
            <div class="alert alert-danger">{{ errorMessage }}</div>
          }

          <form [formGroup]="offeringForm" (ngSubmit)="onSubmit()">
            <div class="form-group">
              <label for="title">Título</label>
              <input type="text" id="title" formControlName="title" placeholder="Ej. Mentoría Java Backend">
            </div>
            
            <div class="form-group">
              <label for="category">Categoría</label>
              <select id="category" formControlName="category">
                <option value="">Selecciona una categoría</option>
                <option value="BACKEND">Backend</option>
                <option value="FRONTEND">Frontend</option>
                <option value="CLOUD">Cloud / DevOps</option>
              </select>
            </div>

            <div class="form-group">
              <label for="price">Precio (COP)</label>
              <input type="number" id="price" formControlName="price" placeholder="Ej. 85000">
            </div>

            <div class="form-group">
              <label for="description">Descripción</label>
              <textarea id="description" formControlName="description" rows="3" placeholder="¿Qué aprenderá el estudiante?"></textarea>
            </div>

            <button type="submit" class="btn btn-primary w-100" [disabled]="offeringForm.invalid || isSubmitting">
              {{ isSubmitting ? 'Publicando...' : 'Publicar Mentoría' }}
            </button>
          </form>
        </div>

        <!-- Lista de mentorías extraída de la Base de Datos -->
        <div class="card list-card">
          <h2>Mis Mentorías Activas</h2>

          <div class="offerings-list">
            @for (offering of myOfferings; track offering.id) {
              <div class="offering-item">
                <div class="offering-info">
                  <h3>{{ offering.title }}</h3>
                  <span class="badge">{{ offering.category }}</span>
                  <p class="price">\${{ offering.price }}</p>
                </div>
                <div class="offering-actions">
                  <a class="btn btn-outline btn-sm" [routerLink]="['/provider/offerings', offering.id, 'horarios']">Horarios</a>
                  <button class="btn btn-danger btn-sm" (click)="deleteOffering(offering.id)">Eliminar</button>
                </div>
              </div>
            } @empty {
              <p class="muted">No tienes mentorías publicadas aún. ¡Crea la primera!</p>
            }
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .section { padding: 40px 20px; }
    .dashboard-header { margin-bottom: 30px; }
    .dashboard-grid { display: grid; grid-template-columns: 1fr 1.5fr; gap: 30px; align-items: start; }
    @media (max-width: 768px) { .dashboard-grid { grid-template-columns: 1fr; } }
    .card { background: #fff; padding: 24px; border-radius: 12px; border: 1px solid #e7ebf0; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-weight: 600; margin-bottom: 8px; font-size: 0.9rem; }
    .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; }
    .w-100 { width: 100%; }
    .btn { padding: 10px 16px; border-radius: 6px; cursor: pointer; border: none; font-weight: 600; }
    .btn-primary { background: #000; color: #fff; }
    .btn-primary:disabled { background: #ccc; cursor: not-allowed; }
    .btn-outline { background: transparent; border: 1px solid #000; color: #000; }
    .btn-danger { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
    .btn-sm { padding: 6px 12px; font-size: 0.85rem; }
    .alert { padding: 12px; border-radius: 6px; margin-bottom: 16px; font-size: 0.9rem; }
    .alert-success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
    .alert-danger { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .offerings-list { display: flex; flex-direction: column; gap: 16px; margin-top: 20px; }
    .offering-item { display: flex; justify-content: space-between; align-items: center; padding: 16px; border: 1px solid #e7ebf0; border-radius: 8px; }
    .offering-info h3 { margin: 0 0 8px 0; font-size: 1.1rem; }
    .badge { background: #f3f6fb; padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600; }
    .price { font-weight: bold; margin-top: 8px; color: #2563eb; }
    .offering-actions { display: flex; flex-direction: column; gap: 8px; }
  `]
})
export class ProviderOfferingsComponent implements OnInit {
  offeringForm: FormGroup;
  offeringService = inject(OfferingService);
  
  isSubmitting = false;
  successMessage = '';
  errorMessage = '';
  myOfferings: Offering[] = [];

  constructor(private fb: FormBuilder) {
    this.offeringForm = this.fb.group({
      title: ['', Validators.required],
      category: ['', Validators.required],
      price: ['', [Validators.required, Validators.min(0)]],
      description: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.loadOfferings();
  }

  loadOfferings() {
    this.offeringService.getProviderOfferings().subscribe({
      next: (data) => this.myOfferings = data,
      error: (err) => console.error('Error cargando mentorías', err)
    });
  }

  onSubmit() {
    if (this.offeringForm.invalid) return;
    
    this.isSubmitting = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.offeringService.createProviderOffering(this.offeringForm.value).subscribe({
      next: () => {
        this.successMessage = '¡Mentoría publicada con éxito!';
        this.offeringForm.reset({ category: '' }); // Limpiamos el formulario
        this.isSubmitting = false;
        this.loadOfferings(); // Recarga la lista automáticamente
      },
      error: (err) => {
        this.errorMessage = 'Ocurrió un error al publicar la mentoría.';
        this.isSubmitting = false;
        console.error(err);
      }
    });
  }

  deleteOffering(id: string) {
    if(confirm('¿Estás seguro de eliminar esta mentoría?')) {
      this.offeringService.deleteProviderOffering(id).subscribe({
        next: () => {
          this.errorMessage = '';
          this.successMessage = 'Mentoría eliminada correctamente.';
          this.loadOfferings();
        },
        error: (err) => {
          this.successMessage = '';
          this.errorMessage = err?.error?.detail || 'No fue posible eliminar la mentoría.';
        }
      });
    }
  }
}