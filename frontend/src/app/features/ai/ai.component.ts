import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { apiBase } from '../../core/api';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  formattedContent?: SafeHtml;
  timestamp: number;
}

interface StoredChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp?: number;
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './ai.html',
  styleUrls: ['./ai.css']
})
export class AiComponent implements OnInit {
  private readonly STORAGE_KEY = 'skillbridge_ai_chat_history';
  readonly maxLength = 500;

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;

  readonly suggestions = [
    { category: 'backend', label: 'Entrevista backend Java', goal: 'Quiero prepararme para una entrevista técnica de backend con Java y Spring Boot.' },
    { category: 'frontend', label: 'Dominar Angular', goal: 'Quiero mejorar mis habilidades en Angular, RxJS y arquitectura frontend.' },
    { category: 'cloud', label: 'Desplegar en la nube', goal: 'Quiero aprender a desplegar una aplicación distribuida con Docker en la nube.' }
  ];

  goal = '';
  messages: ChatMessage[] = [];
  error = '';
  loading = false;
  unauthorized = false;
  copiedId: string | null = null;

  // Propiedades de compatibilidad
  get answer(): string {
    const lastAssistant = [...this.messages].reverse().find(m => m.role === 'assistant');
    return lastAssistant ? lastAssistant.content : '';
  }

  get formattedAnswer(): SafeHtml {
    const lastAssistant = [...this.messages].reverse().find(m => m.role === 'assistant');
    return lastAssistant?.formattedContent ?? '';
  }

  constructor(
    private http: HttpClient,
    private sanitizer: DomSanitizer,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.restoreHistory();
  }

  useSuggestion(goal: string): void {
    this.goal = goal;
  }

  handleEnter(event: Event): void {
    const ke = event as KeyboardEvent;
    if (ke.shiftKey) return;
    ke.preventDefault();
    this.ask();
  }

  ask(): void {
    if (!this.goal.trim() || this.loading) return;

    const currentGoal = this.goal.trim();
    this.error = '';
    this.unauthorized = false;

    // Historial previo antes del mensaje actual
    const historyPayload = this.messages.map(m => ({
      role: m.role,
      content: m.content
    }));

    // Agregar mensaje del usuario a la vista
    const userMsg: ChatMessage = {
      id: `u-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      role: 'user',
      content: currentGoal,
      timestamp: Date.now()
    };
    this.messages.push(userMsg);
    this.goal = '';
    this.loading = true;
    this.scrollToBottom();

    this.http.post<{ recommendation: string }>(`${apiBase()}/ai/recommendations`, {
      goal: currentGoal,
      history: historyPayload
    }).subscribe({
      next: r => {
        const assistantMsg: ChatMessage = {
          id: `a-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          role: 'assistant',
          content: r.recommendation,
          formattedContent: this.parseMarkdown(r.recommendation),
          timestamp: Date.now()
        };
        this.messages.push(assistantMsg);
        this.saveHistory();
        this.loading = false;
        this.scrollToBottom();
      },
      error: e => {
        this.unauthorized = e?.status === 401;
        this.error = this.unauthorized
          ? 'Necesitas iniciar sesión para usar el asistente.'
          : e?.error?.detail || e?.error?.message || this.fallbackError(e?.status);
        this.loading = false;
        this.scrollToBottom();
      }
    });
  }

  // ==========================================
  // NAVEGACIÓN Y CLICK EN ENLACES DE MENTORÍAS
  // ==========================================

  onMessageClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const link = target.closest('a');
    if (link) {
      const href = link.getAttribute('href') || link.getAttribute('data-link');
      if (href && href.startsWith('/')) {
        event.preventDefault();
        this.router.navigateByUrl(href);
      }
    }
  }

  // ==========================================
  // PERSISTENCIA Y NUEVA CONVERSACIÓN
  // ==========================================

  reset(): void {
    this.goal = '';
    this.messages = [];
    this.error = '';
    this.unauthorized = false;
    this.clearHistory();
  }

  copy(text: string, id: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copiedId = id;
      setTimeout(() => {
        if (this.copiedId === id) this.copiedId = null;
      }, 2000);
    });
  }

  private saveHistory(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      const payload: StoredChatMessage[] = this.messages.map(m => ({
        role: m.role,
        content: m.content,
        timestamp: m.timestamp
      }));
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(payload));
    } catch (e) {
      console.warn('[SkillBridge AI] No se pudo guardar el historial en localStorage:', e);
    }
  }

  private restoreHistory(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (raw) {
        const parsed: StoredChatMessage[] = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) {
          this.messages = parsed.map((m, idx) => ({
            id: `restored-${idx}-${m.timestamp || Date.now()}`,
            role: m.role,
            content: m.content,
            formattedContent: m.role === 'assistant' ? this.parseMarkdown(m.content) : undefined,
            timestamp: m.timestamp || Date.now()
          }));
          this.scrollToBottom();
          return;
        }
      }

      // Compatibilidad con almacenamiento previo
      const legacyRaw = localStorage.getItem('skillbridge_ai_last_recommendation');
      if (legacyRaw) {
        const legacy = JSON.parse(legacyRaw);
        if (legacy && typeof legacy.answer === 'string' && legacy.answer.trim().length > 0) {
          if (legacy.goal) {
            this.messages.push({
              id: 'legacy-user',
              role: 'user',
              content: legacy.goal,
              timestamp: legacy.savedAt || Date.now()
            });
          }
          this.messages.push({
            id: 'legacy-asst',
            role: 'assistant',
            content: legacy.answer,
            formattedContent: this.parseMarkdown(legacy.answer),
            timestamp: legacy.savedAt || Date.now()
          });
          this.scrollToBottom();
        }
      }
    } catch (e) {
      console.warn('[SkillBridge AI] Error al restaurar historial desde localStorage:', e);
    }
  }

  private clearHistory(): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      localStorage.removeItem(this.STORAGE_KEY);
      localStorage.removeItem('skillbridge_ai_last_recommendation');
    } catch (e) {
      console.warn('[SkillBridge AI] Error al limpiar historial en localStorage:', e);
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.scrollContainer?.nativeElement) {
        this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
      }
    }, 50);
  }

  // ==========================================
  // FORMATO MARKDOWN Y ENLACES A MENTORÍAS
  // ==========================================

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

    // Enlaces de mentorías hacia su detalle: [Título](/services/<id>)
    html = html.replace(
      /\[([^\]]+)\]\(([^)]+)\)/g,
      '<a href="$2" class="chat-offering-link" data-link="$2">$1 <span class="chat-link-arrow">→</span></a>'
    );

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
    if (status === 429) return 'El asistente recibió demasiadas consultas; espera un momento e inténtalo de nuevo.';
    if (status === 502 || status === 503 || status === 504) return 'DeepSeek no está disponible en este momento; inténtalo de nuevo.';
    return 'No fue posible generar la recomendación; inténtalo de nuevo.';
  }
}
