import { Component, ElementRef, OnInit, inject, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OfferingService, Offering, OfferingInput, OfferingLevel, SyllabusTopic, WeekDay } from '../core/offering.service';
import { AlertService } from '../shared/alert.service';
import {
  DURATION_OPTIONS, LEVELS, MAX_TOPICS, MIN_TOPICS, PLATFORM_COMMISSION, WEEK_DAYS,
  formatDays, formatDuration, formatPrice, levelLabel, platformFee, providerEarnings, sortDays
} from '../shared/offering-format';

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

/** El temario necesita al menos 2 temas (HU-20); el backend responde 400 con menos */
function minTopics(control: AbstractControl): ValidationErrors | null {
  return (control as FormArray).length >= MIN_TOPICS ? null : { minTopics: true };
}

/** Al menos un día de la semana */
function someDay(control: AbstractControl): ValidationErrors | null {
  return Array.isArray(control.value) && control.value.length > 0 ? null : { required: true };
}

const WEEKDAYS: WeekDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const ALL_DAYS: WeekDay[] = WEEK_DAYS.map(d => d.value);

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
  templateUrl: './provider-offerings.component.html',
  styleUrls: ['./provider-offerings.component.css']
})
export class ProviderOfferingsComponent implements OnInit {
  offeringForm: FormGroup;
  offeringService = inject(OfferingService);
  private readonly alerts = inject(AlertService);
  private readonly formCard = viewChild<ElementRef<HTMLElement>>('formCard');

  // Opciones y textos de la ficha (HU-20)
  readonly levels = LEVELS;
  readonly weekDays = WEEK_DAYS;
  readonly durationOptions = DURATION_OPTIONS;
  readonly minTopics = MIN_TOPICS;
  readonly maxTopics = MAX_TOPICS;
  readonly weekdays = WEEKDAYS;
  readonly allDays = ALL_DAYS;
  readonly commissionPercent = Math.round(PLATFORM_COMMISSION * 100);
  readonly formatPrice = formatPrice;
  readonly formatDuration = formatDuration;
  readonly formatDays = formatDays;
  readonly levelLabel = levelLabel;

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
      description: ['', [Validators.required, notBlank, Validators.maxLength(1200)]],
      // ---- Ficha de la mentoría (HU-20) ----
      level: ['BASICO' as OfferingLevel, Validators.required],
      durationMinutes: [60, Validators.required],
      workingDays: [[...WEEKDAYS], someDay],
      syllabus: this.fb.array([this.topicGroup(), this.topicGroup()], minTopics)
    });
  }

  get syllabus(): FormArray { return this.offeringForm.get('syllabus') as FormArray; }

  /** Lo que recibe el proveedor por sesión después de la comisión; null mientras el precio no sea válido */
  earnings(): { price: number; net: number; fee: number } | null {
    const price = Number(this.offeringForm.get('price')?.value);
    if (!Number.isFinite(price) || price <= 0) return null;
    return { price, net: providerEarnings(price), fee: platformFee(price) };
  }

  // ---- Temario dinámico ----

  addTopic(topic?: SyllabusTopic) {
    if (this.syllabus.length >= MAX_TOPICS) return;
    this.syllabus.push(this.topicGroup(topic));
  }

  removeTopic(index: number) {
    if (this.syllabus.length <= MIN_TOPICS) return;
    this.syllabus.removeAt(index);
  }

  topicHasError(index: number): boolean {
    const group = this.syllabus.at(index);
    return group.invalid && group.touched;
  }

  // ---- Días de la semana ----

  hasDay(day: WeekDay): boolean {
    return (this.offeringForm.get('workingDays')?.value as WeekDay[]).includes(day);
  }

  toggleDay(day: WeekDay) {
    const days = this.offeringForm.get('workingDays')?.value as WeekDay[];
    this.setDays(days.includes(day) ? days.filter(d => d !== day) : [...days, day]);
  }

  setDays(days: WeekDay[]) {
    const control = this.offeringForm.get('workingDays')!;
    control.setValue(sortDays(days));
    control.markAsTouched();
  }

  private topicGroup(topic?: SyllabusTopic): FormGroup {
    return this.fb.group({
      title: [topic?.title ?? '', [Validators.required, notBlank, Validators.maxLength(120)]],
      description: [topic?.description ?? '', [Validators.required, notBlank, Validators.maxLength(300)]]
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

  /** Carga la mentoría en el formulario para editarla */
  startEdit(offering: Offering) {
    this.editingId = offering.id;
    this.clearMessages();
    this.fillForm(offering);
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
      error: (err: HttpErrorResponse) => {
        this.errorMessage = err.status === 400 && err.error?.detail
          ? `Revisa los datos: ${err.error.detail}`
          : 'Ocurrió un error al publicar la mentoría.';
        this.isSubmitting = false;
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

  /** Valores limpios: sin espacios sobrantes, precio y duración como número y días en orden */
  private formValue(): OfferingInput {
    const { title, description, category, price, level, durationMinutes, workingDays, syllabus } = this.offeringForm.value;
    return {
      title: String(title).trim(),
      description: String(description).trim(),
      category,
      price: Number(price),
      syllabus: (syllabus as SyllabusTopic[]).map(t => ({ title: t.title.trim(), description: t.description.trim() })),
      durationMinutes: Number(durationMinutes),
      level,
      workingDays: sortDays(workingDays)
    };
  }

  /** Llena el formulario con la mentoría a editar; el temario siempre muestra al menos 2 filas */
  private fillForm(offering: Offering) {
    const topics = offering.syllabus ?? [];
    this.syllabus.clear();
    topics.slice(0, MAX_TOPICS).forEach(t => this.syllabus.push(this.topicGroup(t)));
    while (this.syllabus.length < MIN_TOPICS) this.syllabus.push(this.topicGroup());

    this.offeringForm.reset({
      title: offering.title,
      category: offering.category,
      price: offering.price,
      description: offering.description,
      level: offering.level ?? 'BASICO',
      durationMinutes: offering.durationMinutes ?? 60,
      workingDays: sortDays(offering.workingDays ?? ALL_DAYS),
      syllabus: this.syllabus.value
    });
  }

  private resetForm() {
    this.syllabus.clear();
    this.syllabus.push(this.topicGroup());
    this.syllabus.push(this.topicGroup());
    this.offeringForm.reset({
      title: '', category: '', price: null, description: '',
      level: 'BASICO', durationMinutes: 60, workingDays: [...WEEKDAYS]
    });
  }

  private clearMessages() {
    this.successMessage = '';
    this.errorMessage = '';
  }
}
