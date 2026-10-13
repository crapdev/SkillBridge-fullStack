import { Component, ElementRef, OnInit, inject, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OfferingService, Offering, OfferingInput } from '../core/offering.service';
import { AlertService } from '../shared/alert.service';

/** El backend rechaza precios en cero o negativos; el formulario aplica la misma regla */
function positivePrice(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === '' || value === undefined) return null;
  return Number(value) > 0 ? null : { positive: true };
}

/** Rechaza textos que solo tienen espacios (Validators.required los deja pasar) */
function notBlank(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && value.trim() === '' ? { required: true } : null;
}

const plural = (n: number, singular: string, pluralForm: string) => `${n} ${n === 1 ? singular : pluralForm}`;

/** Texto del diálogo: qué se borra y la regla de los horarios reservados */
function deleteMessage(freeSlots: number): string {
  const slotsText = freeSlots > 0
    ? `También se borrarán sus ${plural(freeSlots, 'horario libre', 'horarios libres')}. `
    : '';
  return `${slotsText}Dejará de aparecer en el catálogo y esta acción no se puede deshacer. ` +
    'Recuerda: no se puede eliminar una mentoría con horarios reservados.';
}

/** Traduce la respuesta del backend a un mensaje para el proveedor */
function deleteErrorMessage(err: HttpErrorResponse): { title: string; message: string } {
  switch (err.status) {
    case 422:
      return {
        title: 'No se puede eliminar esta mentoría',
        message: err.error?.detail || 'Tiene horarios reservados por clientes.'
      };
    case 403:
      return { title: 'Acción no permitida', message: 'Esta mentoría pertenece a otro proveedor.' };
    case 404:
      return { title: 'La mentoría ya no existe', message: 'Actualizamos tu lista.' };
    default:
      return { title: 'No fue posible eliminar la mentoría', message: 'Intenta de nuevo en unos segundos.' };
  }
}

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
        <!-- Formulario para crear o editar una mentoría -->
        <div #formCard class="card form-card" [class.editing]="isEditing">
          <div class="form-heading">
            <h2>{{ isEditing ? 'Editar Mentoría' : 'Nueva Mentoría' }}</h2>
            @if (isEditing) {
              <span class="badge badge-editing">Editando</span>
            }
          </div>
          @if (isEditing) {
            <p class="muted small">Los cambios se verán en el catálogo apenas guardes.</p>
          }

          @if (successMessage) {
            <div class="alert alert-success" role="status">{{ successMessage }}</div>
          }
          @if (errorMessage) {
            <div class="alert alert-danger" role="alert">{{ errorMessage }}</div>
          }

          <form [formGroup]="offeringForm" (ngSubmit)="onSubmit()" novalidate>
            <div class="form-group">
              <label for="title">Título</label>
              <input type="text" id="title" formControlName="title" maxlength="160" placeholder="Ej. Mentoría Java Backend">
              @if (showError('title')) { <small class="field-error">El título es obligatorio.</small> }
            </div>

            <div class="form-group">
              <label for="category">Categoría</label>
              <select id="category" formControlName="category">
                <option value="">Selecciona una categoría</option>
                <option value="BACKEND">Backend</option>
                <option value="FRONTEND">Frontend</option>
                <option value="CLOUD">Cloud / DevOps</option>
              </select>
              @if (showError('category')) { <small class="field-error">Selecciona una categoría.</small> }
            </div>

            <div class="form-group">
              <label for="price">Precio (COP)</label>
               <p class="field-note">
                Ten en cuenta que SkillBridge cobra una comisión del <strong>10%</strong>
                por cada mentoría realizada.
               </p>
              <input type="number" id="price" formControlName="price" placeholder="Ej. 85000">
              <input type="number" id="price" formControlName="price" min="1" step="1000" placeholder="Ej. 85000">
              @if (showError('price')) { <small class="field-error">El precio es obligatorio y debe ser mayor a cero.</small> }
            </div>

            <div class="form-group">
              <label for="description">Descripción</label>
              <textarea id="description" formControlName="description" rows="3" maxlength="1200" placeholder="¿Qué aprenderá el estudiante?"></textarea>
              @if (showError('description')) { <small class="field-error">La descripción es obligatoria.</small> }
            </div>

            <button type="submit" class="btn btn-primary w-100" [disabled]="offeringForm.invalid || isSubmitting">
              {{ submitLabel }}
            </button>
            @if (isEditing) {
              <button type="button" class="btn btn-outline w-100 mt-8" (click)="cancelEdit()" [disabled]="isSubmitting">
                Cancelar edición
              </button>
            }
          </form>
        </div>

        <!-- Lista de mentorías extraída de la Base de Datos -->
        <div class="card list-card">
          <h2>Mis Mentorías Activas</h2>
          <p class="muted small hint">Solo puedes eliminar mentorías sin horarios reservados.</p>

          <div class="offerings-list">
            @for (offering of myOfferings; track offering.id) {
              <div class="offering-item" [class.selected]="offering.id === editingId">
                <div class="offering-info">
                  <h3>{{ offering.title }}</h3>
                  <span class="badge">{{ offering.category }}</span>
                  <p class="price">{{ formatPrice(offering.price) }}</p>
                </div>
                <div class="offering-actions">
                  <button type="button" class="btn btn-outline btn-sm" (click)="startEdit(offering)"
                          [disabled]="offering.id === editingId">Editar</button>
                  <a class="btn btn-outline btn-sm" [routerLink]="['/provider/offerings', offering.id, 'horarios']">Horarios</a>
                  <button type="button" class="btn btn-danger btn-sm" (click)="deleteOffering(offering)"
                          [disabled]="deletingId === offering.id">
                    {{ deletingId === offering.id ? 'Eliminando...' : 'Eliminar' }}
                  </button>
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
    .form-card.editing { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, .12); }
    .form-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .small { font-size: 0.85rem; margin: 4px 0 16px; }
    .form-group { margin-bottom: 16px; }
    .form-group label { display: block; font-weight: 600; margin-bottom: 8px; font-size: 0.9rem; }
    .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; }
    .field-note { font-size: 0.85rem; line-height: 1.4; color: #667085; margin: 0 0 8px 0; }
    .field-note strong { color: #13213c; }
    .form-group input.ng-invalid.ng-touched, .form-group select.ng-invalid.ng-touched, .form-group textarea.ng-invalid.ng-touched { border-color: #dc2626; }
    .field-error { display: block; margin-top: 6px; color: #b91c1c; font-size: 0.8rem; }
    .w-100 { width: 100%; }
    .mt-8 { margin-top: 8px; }
    .btn { padding: 10px 16px; border-radius: 6px; cursor: pointer; border: none; font-weight: 600; }
    .btn-primary { background: #000; color: #fff; }
    .btn-primary:disabled { background: #ccc; cursor: not-allowed; }
    .btn-outline { background: transparent; border: 1px solid #000; color: #000; text-align: center; text-decoration: none; }
    .btn-outline:disabled { opacity: .5; cursor: not-allowed; }
    .btn-danger { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
    .btn-danger:disabled { opacity: .6; cursor: not-allowed; }
    .hint { margin: 4px 0 0; }
    .btn-sm { padding: 6px 12px; font-size: 0.85rem; }
    .alert { padding: 12px; border-radius: 6px; margin-bottom: 16px; font-size: 0.9rem; }
    .alert-success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
    .alert-danger { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .offerings-list { display: flex; flex-direction: column; gap: 16px; margin-top: 20px; }
    .offering-item { display: flex; justify-content: space-between; align-items: center; padding: 16px; border: 1px solid #e7ebf0; border-radius: 8px; }
    .offering-item.selected { border-color: #2563eb; background: #f5f8ff; }
    .offering-info h3 { margin: 0 0 8px 0; font-size: 1.1rem; }
    .badge { background: #f3f6fb; padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600; }
    .badge-editing { background: #e8efff; color: #1d4ed8; }
    .price { font-weight: bold; margin-top: 8px; color: #2563eb; }
    .offering-actions { display: flex; flex-direction: column; gap: 8px; }
  `]
})
export class ProviderOfferingsComponent implements OnInit {
  offeringForm: FormGroup;
  offeringService = inject(OfferingService);
  private readonly alerts = inject(AlertService);
  private readonly formCard = viewChild<ElementRef<HTMLElement>>('formCard');
  private readonly priceFormat = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

  isSubmitting = false;
  successMessage = '';
  errorMessage = '';
  myOfferings: Offering[] = [];
  /** Id de la mentoría que se está editando; null = el formulario crea una nueva */
  editingId: string | null = null;
  /** Id de la mentoría que se está eliminando, para deshabilitar su botón mientras responde el backend */
  deletingId: string | null = null;

  constructor(private fb: FormBuilder) {
    this.offeringForm = this.fb.group({
      title: ['', [Validators.required, notBlank, Validators.maxLength(160)]],
      category: ['', Validators.required],
      price: [null as number | null, [Validators.required, positivePrice]],
      description: ['', [Validators.required, notBlank, Validators.maxLength(1200)]]
    });
  }

  get isEditing(): boolean { return this.editingId !== null; }

  get submitLabel(): string {
    if (this.isEditing) return this.isSubmitting ? 'Guardando...' : 'Guardar cambios';
    return this.isSubmitting ? 'Publicando...' : 'Publicar Mentoría';
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

  showError(field: string): boolean {
    const control = this.offeringForm.get(field);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  formatPrice(price: number): string {
    return this.priceFormat.format(price);
  }

  /** Carga la mentoría en el formulario para editarla */
  startEdit(offering: Offering) {
    this.editingId = offering.id;
    this.clearMessages();
    this.offeringForm.reset({
      title: offering.title,
      category: offering.category,
      price: offering.price,
      description: offering.description
    });
    this.formCard()?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelEdit() {
    this.editingId = null;
    this.clearMessages();
    this.resetForm();
  }

  onSubmit() {
    if (this.offeringForm.invalid) {
      this.offeringForm.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.clearMessages();

    if (this.editingId) {
      this.saveChanges(this.editingId, this.formValue());
    } else {
      this.create(this.formValue());
    }
  }

  /**
   * HU-19: antes de eliminar se revisan los horarios. Si alguno está reservado se explica por qué no se puede;
   * si no, se pide confirmación indicando cuántos horarios libres se borrarán con la mentoría.
   */
  async deleteOffering(offering: Offering) {
    if (this.deletingId) return;
    this.clearMessages();

    const slots = await firstValueFrom(this.offeringService.getProviderSlots(offering.id)).catch(() => null);
    const reserved = slots?.filter(s => s.reserved).length ?? 0;
    const free = slots ? slots.length - reserved : 0;

    if (reserved > 0) {
      this.alerts.error(
        'No se puede eliminar esta mentoría',
        `"${offering.title}" tiene ${plural(reserved, 'horario reservado', 'horarios reservados')} por clientes. ` +
        'Una mentoría con horarios reservados no se puede eliminar.'
      );
      return;
    }

    const confirmed = await this.alerts.confirm({
      title: `¿Eliminar "${offering.title}"?`,
      message: deleteMessage(free),
      confirmText: 'Sí, eliminar',
      cancelText: 'Cancelar',
      danger: true
    });
    if (!confirmed) return;

    this.deletingId = offering.id;
    this.offeringService.deleteProviderOffering(offering.id).subscribe({
      next: () => {
        this.deletingId = null;
        this.myOfferings = this.myOfferings.filter(o => o.id !== offering.id);
        if (offering.id === this.editingId) this.cancelEdit();
        this.alerts.success('Mentoría eliminada', `"${offering.title}" ya no aparece en el catálogo.`);
      },
      error: (err: HttpErrorResponse) => {
        this.deletingId = null;
        const { title, message } = deleteErrorMessage(err);
        this.alerts.error(title, message);
        // Si ya no existe o no es nuestra, la lista local está desactualizada
        if (err.status === 403 || err.status === 404) this.loadOfferings();
      }
    });
  }

  private create(data: OfferingInput) {
    this.offeringService.createProviderOffering(data).subscribe({
      next: () => {
        this.successMessage = '¡Mentoría publicada con éxito!';
        this.resetForm();
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

  private saveChanges(id: string, data: OfferingInput) {
    this.offeringService.updateProviderOffering(id, data).subscribe({
      next: (updated) => {
        // Se reemplaza en la lista sin esperar otra consulta al backend
        this.myOfferings = this.myOfferings.map(o => o.id === updated.id ? updated : o);
        this.editingId = null;
        this.resetForm();
        this.isSubmitting = false;
        this.successMessage = 'Cambios guardados. El catálogo ya muestra la versión actualizada.';
      },
      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.errorMessage = this.updateErrorMessage(err);
        if (err.status === 403 || err.status === 404) {
          // La mentoría ya no es editable: se sale del modo edición y se refresca la lista
          this.editingId = null;
          this.resetForm();
          this.loadOfferings();
        }
      }
    });
  }

  private updateErrorMessage(err: HttpErrorResponse): string {
    switch (err.status) {
      case 400: return err.error?.detail ? `Revisa los datos: ${err.error.detail}` : 'Revisa los datos del formulario.';
      case 403: return 'No puedes editar una mentoría que pertenece a otro proveedor.';
      case 404: return 'La mentoría ya no existe. Actualizamos tu lista.';
      default: return err.error?.detail || 'No fue posible guardar los cambios. Intenta de nuevo.';
    }
  }

  /** Valores limpios: sin espacios sobrantes y con el precio como número */
  private formValue(): OfferingInput {
    const { title, description, category, price } = this.offeringForm.value;
    return {
      title: String(title).trim(),
      description: String(description).trim(),
      category,
      price: Number(price)
    };
  }

  private resetForm() {
    this.offeringForm.reset({ title: '', category: '', price: null, description: '' });
  }

  private clearMessages() {
    this.successMessage = '';
    this.errorMessage = '';
  }
}
