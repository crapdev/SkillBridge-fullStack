import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { OfferingReviewListComponent } from './offering-review-list.component';
import { AdminOfferingsService } from '../data/admin-offerings.service';
import { AdminOffering } from '../data/admin-offering.model';
import { ToastService } from '../shared/toast.service';

function offering(id: string, title: string): AdminOffering {
  return {
    id, title, description: 'Pods y despliegues', category: 'CLOUD', price: 130000,
    syllabus: [{ title: 'Pods', description: 'Conceptos' }, { title: 'Helm', description: 'Charts' }],
    durationMinutes: 90, level: 'AVANZADO', workingDays: ['MONDAY', 'FRIDAY'],
    reviewStatus: 'PENDING_REVIEW', rejectionReason: null,
    providerId: 'p1', providerName: 'Andrés Ruiz', providerEmail: 'andres@test.com'
  };
}

describe('OfferingReviewListComponent (HU-21)', () => {
  let fixture: ComponentFixture<OfferingReviewListComponent>;
  let service: jasmine.SpyObj<AdminOfferingsService>;
  let toast: jasmine.SpyObj<ToastService>;
  let el: HTMLElement;

  function create(pending = of([offering('1', 'Kubernetes desde cero'), offering('2', 'Testing en Angular')])) {
    service = jasmine.createSpyObj<AdminOfferingsService>('AdminOfferingsService', ['pending', 'approve', 'reject']);
    service.pending.and.returnValue(pending);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [OfferingReviewListComponent],
      providers: [{ provide: AdminOfferingsService, useValue: service }, { provide: ToastService, useValue: toast }]
    });
    fixture = TestBed.createComponent(OfferingReviewListComponent);
    fixture.detectChanges();
    el = fixture.nativeElement;
  }

  const titles = () => Array.from(el.querySelectorAll('.review h2')).map(h => h.textContent?.trim());
  const cardButton = (index: number, text: string) =>
    Array.from(el.querySelectorAll('.review')[index].querySelectorAll('button')).find(b => b.textContent?.includes(text))!;
  const dialogButton = (text: string) =>
    Array.from(el.querySelectorAll('dialog footer button')).find(b => b.textContent?.includes(text)) as HTMLButtonElement;

  beforeAll(() => {
    // jsdom/Chrome headless: showModal puede no existir en todos los entornos de prueba
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
    }
  });

  it('muestra cada mentoría pendiente con su ficha y quién la propone', () => {
    create();

    expect(titles()).toEqual(['Kubernetes desde cero', 'Testing en Angular']);
    expect(el.textContent).toContain('Andrés Ruiz');
    expect(el.textContent).toContain('Avanzado');
    expect(el.textContent).toContain('Temario · 2 temas');
  });

  it('al aprobar, la mentoría sale de la cola y se avisa', () => {
    create();
    service.approve.and.returnValue(of(offering('1', 'Kubernetes desde cero')));

    cardButton(0, 'Aprobar').click();
    fixture.detectChanges();
    dialogButton('Aprobar').click();
    fixture.detectChanges();

    expect(service.approve).toHaveBeenCalledWith('1');
    expect(titles()).toEqual(['Testing en Angular']);
    expect(toast.success).toHaveBeenCalled();
  });

  it('al rechazar, envía el motivo escrito', () => {
    create();
    service.reject.and.returnValue(of(offering('2', 'Testing en Angular')));

    cardButton(1, 'Rechazar').click();
    fixture.detectChanges();
    const textarea = el.querySelector('dialog textarea') as HTMLTextAreaElement;
    textarea.value = 'Falta un tema de pruebas e2e';
    textarea.dispatchEvent(new Event('input'));
    dialogButton('Rechazar').click();
    fixture.detectChanges();

    expect(service.reject).toHaveBeenCalledWith('2', 'Falta un tema de pruebas e2e');
    expect(titles()).toEqual(['Kubernetes desde cero']);
  });

  it('si el backend rechaza la decisión, la mentoría sigue en la cola y se muestra el error', () => {
    create();
    service.approve.and.returnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { detail: 'Ya fue revisada' } })));

    cardButton(0, 'Aprobar').click();
    fixture.detectChanges();
    dialogButton('Aprobar').click();
    fixture.detectChanges();

    expect(titles().length).toBe(2);
    expect(toast.error).toHaveBeenCalledWith('Ya fue revisada');
  });

  it('sin pendientes muestra que todo está al día', () => {
    create(of([]));

    expect(el.textContent).toContain('No hay mentorías por revisar');
  });

  it('si falla la carga, ofrece reintentar', () => {
    create(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No fue posible cargar');
    expect(el.textContent).toContain('Reintentar');
  });
});
