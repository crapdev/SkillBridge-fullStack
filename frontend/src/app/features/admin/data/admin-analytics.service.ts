import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { apiBase } from '../../../core/api';
import { KEEP_SESSION_ON_401 } from '../../../core/auth.interceptor';
import { AnalyticsDataset, SparkAnalytics } from './admin-analytics.model';

const context = () => new HttpContext().set(KEEP_SESSION_ON_401, true);

/** Resultados del pipeline de PySpark. Se provee en ADMIN_ROUTES para usar el HttpClient del panel (y sus datos simulados). */
@Injectable()
export class AdminAnalyticsService {
  private http = inject(HttpClient);

  results(dataset: AnalyticsDataset) {
    const params = new HttpParams().set('dataset', dataset);
    return this.http.get<SparkAnalytics>(`${apiBase()}/admin/analytics`, { params, context: context() });
  }
}
