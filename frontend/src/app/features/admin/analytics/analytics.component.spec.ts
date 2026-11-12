import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpRequest, HttpParams } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { AnalyticsComponent } from './analytics.component';
import { AdminAnalyticsService } from '../data/admin-analytics.service';
import { SparkAnalytics } from '../data/admin-analytics.model';
import { handleAnalytics } from '../data/admin-analytics.mock';

const request = (dataset: string) =>
  new HttpRequest('GET', '/api/admin/analytics', { params: new HttpParams().set('dataset', dataset) });

const load = async (dataset: string) =>
  (await firstValueFrom(handleAnalytics(request(dataset)))).body as SparkAnalytics;

describe('AnalyticsComponent', () => {
  let fixture: ComponentFixture<AnalyticsComponent>;
  let service: jasmine.SpyObj<AdminAnalyticsService>;
  let fixtures: SparkAnalytics;
  let snapshot: SparkAnalytics;

  beforeEach(async () => {
    fixtures = await load('fixtures');
    snapshot = await load('snapshot');
    service = jasmine.createSpyObj<AdminAnalyticsService>('AdminAnalyticsService', ['results']);
    service.results.and.callFake(d => of(d === 'fixtures' ? fixtures : snapshot));

    TestBed.configureTestingModule({
      imports: [AnalyticsComponent],
      providers: [{ provide: AdminAnalyticsService, useValue: service }]
    });
    fixture = TestBed.createComponent(AnalyticsComponent);
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const click = (label: string) => {
    const btn = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find(b => b.textContent?.trim() === label)!;
    btn.click();
    fixture.detectChanges();
  };

  it('el mock de fixtures reproduce los valores que esperan las pruebas de PySpark', () => {
    const tasa = Object.fromEntries(fixtures.tasaCancelacion.map(r => [r.provider_name, r]));
    expect(tasa['prov_02']).toEqual(jasmine.objectContaining({ reservas: 8, canceladas: 1, tasa_cancelacion: 12.5 }));
    expect(tasa['prov_03']).toEqual(jasmine.objectContaining({ reservas: 4, canceladas: 1, tasa_cancelacion: 25 }));
  });

  it('el snapshot es coherente: estados y proveedores suman las mismas reservas y canceladas', () => {
    const porEstado = snapshot.reservasPorEstado.reduce((s, r) => s + r.reservas, 0);
    const porProveedor = snapshot.tasaCancelacion.reduce((s, r) => s + r.reservas, 0);
    const canceladas = snapshot.tasaCancelacion.reduce((s, r) => s + r.canceladas, 0);
    expect(porEstado).toBe(porProveedor);
    expect(snapshot.reservasPorEstado.find(r => r.status === 'CANCELLED')!.reservas).toBe(canceladas);
  });

  it('muestra los indicadores del snapshot por defecto', () => {
    expect(service.results).toHaveBeenCalledWith('snapshot');
    expect(text()).toContain('240');
    expect(text()).toContain('14.17 %');
  });

  it('cambia a los fixtures y la tasa global sale de sumar, no de promediar', () => {
    click('Fixtures de prueba');
    expect(service.results).toHaveBeenCalledWith('fixtures');
    // 2 canceladas de 12 = 16.67 %, no el promedio de 12.5 y 25 (18.75 %)
    expect(text()).toContain('16.67 %');
  });

  it('el umbral decide qué proveedores quedan en alerta', () => {
    const alertRows = () => (fixture.nativeElement as HTMLElement).querySelectorAll('.bar-row.alert').length;
    expect(alertRows()).toBe(3); // Mariana 33.33, Carlos 25, Gabriel 22.73 > 20
    const range = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=range]')!;
    range.value = '30';
    range.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(alertRows()).toBe(1);
  });

  it('muestra el detalle del cálculo del proveedor elegido', () => {
    const row = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.bar-row'))
      .find(b => b.textContent?.includes('Ana Pérez'))!;
    row.click();
    fixture.detectChanges();
    expect(text()).toContain('6 ÷ 48 × 100 = 12.50 %');
  });

  it('avisa cuando el backend no responde', () => {
    service.results.and.returnValue(throwError(() => new Error('404')));
    click('Fixtures de prueba');
    expect(text()).toContain('/api/admin/analytics');
  });
});
