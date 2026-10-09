import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { apiBase } from '../../core/api';

interface StoredAiState {
  goal: string;
  answer: string;
  savedAt: number;
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './ai.html',
  styleUrls: ['./ai.css']
})
export class AiComponent implements OnInit {
  private readonly STORAGE_KEY = 'skillbridge_ai_last_recommendation';
  readonly maxLength = 500;

  readonly suggestions = [
    { category: 'backend', label: 'Entrevista backend Java', goal: 'Quiero prepararme para una entrevista técnica de backend con Java y Spring Boot.' },
    { category: 'frontend', label: 'Dominar Angular', goal: 'Quiero mejorar mis habilidades en Angular, RxJS y arquitectura frontend.' },
    { category: 'cloud', label: 'Desplegar en la nube', goal: 'Quiero aprender a desplegar una aplicación distribuida con Docker en la nube.' }
  ];

  goal = '';
  answer = '';
  formattedAnswer: SafeHtml = '';
  error = '';
  loading = false;
  unauthorized = false;
  copied = false;

  constructor(
    private http: HttpClient,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.restoreState();
  }

  useSuggestion(goal: string): void {
    this.goal = goal;
  }

  ask(): void {
    if (!this.goal.trim() || this.loading) return;

    this.error = '';
    this.answer = '';
    this.formattedAnswer = '';
    this.unauthorized = false;
    this.copied = false;
    this.loading = true;

    this.http.post<{ recommendation: string }>(`${apiBase()}/ai/recommendations`, {
      goal: this.goal.trim()
    }).subscribe({
      next: r => {
        this.answer = r.recommendation;
        this.formattedAnswer = this.parseMarkdown(r.recommendation);
        this.saveState(); // <-- Persistir automáticamente tras respuesta exitosa
        this.loading = false;
      },
      error: e => {
        this.unauthorized = e?.status === 401;
        this.error = this.unauthorized
          ? 'Necesitas iniciar sesión para usar el asistente.'
          : e?.error?.detail || this.fallbackError(e?.status);
        this.loading = false;
      }
    });
  }

  // ==========================================
  // FUNCIONES DE PERSISTENCIA (LocalStorage)
  // ==========================================

  private saveState(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      const payload: StoredAiState = {
        goal: this.goal,
        answer: this.answer,
        savedAt: Date.now()
      };
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(payload));
    } catch (e) {
      console.warn('[SkillBridge AI] No se pudo guardar el estado en localStorage:', e);
    }
  }

  private restoreState(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (!raw) return;

      const state: StoredAiState = JSON.parse(raw);
      if (state && typeof state.answer === 'string' && state.answer.trim().length > 0) {
        this.goal = state.goal || '';
        this.answer = state.answer;
        this.formattedAnswer = this.parseMarkdown(state.answer);
      }
    } catch (e) {
      console.warn('[SkillBridge AI] Error al restaurar el estado desde localStorage:', e);
    }
  }

  private clearState(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      localStorage.removeItem(this.STORAGE_KEY);
    } catch (e) {
      console.warn('[SkillBridge AI] Error al limpiar localStorage:', e);
    }
  }

  // ==========================================
  // UTILIDADES
  // ==========================================

  reset(): void {
    this.goal = '';
    this.answer = '';
    this.formattedAnswer = '';
    this.error = '';
    this.clearState(); // <-- Limpiar la memoria al pedir una nueva consulta
  }

  copy(): void {
    navigator.clipboard?.writeText(this.answer).then(() => {
      this.copied = true;
      setTimeout(() => (this.copied = false), 2000);
    });
  }

  private parseMarkdown(text: string): SafeHtml {
    if (!text) return '';

    let html = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    html = html.replace(/^### (.*$)/gim, '<h4 class="md-h4">$1</h4>');
    html = html.replace(/^## (.*$)/gim, '<h3 class="md-h3">$1</h3>');
    html = html.replace(/^# (.*$)/gim, '<h2 class="md-h2">$1</h2>');

    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    html = html.replace(/`(.*?)`/g, '<code class="md-code">$1</code>');

    html = html.replace(/^[*-] (.*$)/gim, '<li>$1</li>');
    html = html.replace(/^\d+\. (.*$)/gim, '<li>$1</li>');
    html = html.replace(/(<li>[\s\S]*?<\/li>(?:\s*<li>[\s\S]*?<\/li>)*)/g, '<ul class="md-list">$1</ul>');

    const blocks = html.split(/\n\s*\n/);
    const formatted = blocks
      .map(b => b.trim())
      .filter(b => b.length > 0)
      .map(b => {
        if (/^<(h[2-4]|ul|ol)/i.test(b)) return b;
        return `<p class="md-p">${b.replace(/\n/g, '<br>')}</p>`;
      })
      .join('');

    return this.sanitizer.bypassSecurityTrustHtml(formatted);
  }

  private fallbackError(status?: number): string {
    if (status === 0) return 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
    if (status === 502 || status === 503 || status === 504) return 'DeepSeek no está disponible en este momento; inténtalo de nuevo.';
    return 'No fue posible generar la recomendación; inténtalo de nuevo.';
  }
}
