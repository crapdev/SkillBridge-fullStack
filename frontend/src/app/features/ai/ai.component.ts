import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiBase } from '../../core/api';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './ai.html',
  styleUrls: ['./ai.css']
})
export class AiComponent {
  readonly maxLength = 500;
  readonly suggestions = [
    { category: 'backend', label: 'Entrevista backend Java', goal: 'Quiero prepararme para una entrevista técnica de backend con Java y Spring Boot.' },
    { category: 'frontend', label: 'Dominar Angular', goal: 'Quiero mejorar mis habilidades en Angular, RxJS y arquitectura frontend.' },
    { category: 'cloud', label: 'Desplegar en la nube', goal: 'Quiero aprender a desplegar una aplicación distribuida con Docker en la nube.' }
  ];

  goal = ''; answer = ''; error = ''; loading = false; unauthorized = false; copied = false;

  constructor(private http: HttpClient) {}

  useSuggestion(goal: string) { this.goal = goal; }

  ask() {
    if (!this.goal.trim() || this.loading) return;
    this.error = ''; this.answer = ''; this.unauthorized = false; this.copied = false; this.loading = true;
    this.http.post<{ recommendation: string }>(`${apiBase()}/ai/recommendations`, { goal: this.goal.trim() }).subscribe({
      next: r => { this.answer = r.recommendation; this.loading = false; },
      error: e => {
        this.unauthorized = e?.status === 401;
        this.error = this.unauthorized
          ? 'Necesitas iniciar sesión para usar el asistente.'
          : e?.error?.detail || this.fallbackError(e?.status);
        this.loading = false;
      }
    });
  }

  private fallbackError(status?: number): string {
    if (status === 0) return 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
    if (status === 502 || status === 503 || status === 504) return 'DeepSeek no está disponible en este momento; inténtalo de nuevo.';
    return 'No fue posible generar la recomendación; inténtalo de nuevo.';
  }

  copy() {
    navigator.clipboard?.writeText(this.answer).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }

  reset() { this.goal = ''; this.answer = ''; this.error = ''; }
}
