import { Component, OnDestroy } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, VerificationSent } from '../../core/auth.service';

/** Segundos de espera entre reenvíos del enlace de verificación. */
export const RESEND_COOLDOWN_S = 60;

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, NgTemplateOutlet],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnDestroy {
  mode: 'login' | 'register' = 'login';
  accountType: 'CUSTOMER' | 'PROVIDER' = 'CUSTOMER';
  name = ''; email = ''; password = '';
  // Registro recibido (HU #39): mientras exista se muestra "Revisa tu correo" en lugar del formulario
  sent: VerificationSent | null = null;
  sentAsProvider = false;
  // Login rechazado porque la cuenta aún no verificó su correo
  unverified = false;
  // Reenvío del enlace: cuenta atrás, petición en curso y su resultado
  resendCooldown = 0;
  resending = false;
  resendNotice = '';
  resendError = '';
  error = '';
  // ?expired=1 lo pone el interceptor cuando el backend rechaza la sesión (cookie vencida)
  sessionExpired = false;
  loading = false;
  showPassword = false;
  showTerms = false;
  readonly year = new Date().getFullYear();
  private cooldownTimer?: ReturnType<typeof setInterval>;

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {
    // ?mode=register abre el registro (botón "Crear cuenta" del header); ?type=provider lo abre como proveedor
    const params = this.route.snapshot.queryParamMap;
    if (params.get('mode') === 'register') this.mode = 'register';
    if (params.get('type') === 'provider') { this.mode = 'register'; this.accountType = 'PROVIDER'; }
    this.sessionExpired = params.get('expired') === '1';
  }

  // Si el usuario sale de la página con la cuenta atrás en marcha, el intervalo no debe seguir vivo
  ngOnDestroy(): void { this.stopCooldown(); }

  toggle(form: NgForm): void {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.error = '';
    this.resetVerification();
    form.resetForm({ name: '', email: this.email, password: '' });
  }

  get isProviderRegistration(): boolean { return this.mode === 'register' && this.accountType === 'PROVIDER'; }

  backToLogin(): void {
    this.resetVerification();
    this.mode = 'login';
    this.password = '';
  }

  // Solo se aceptan rutas internas para evitar redirecciones abiertas (p. ej. returnUrl=//sitio-malicioso.com)
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    if (url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')) return url;
    if (this.auth.hasRole('ADMIN')) return '/admin';
    if (this.auth.hasRole('PROVIDER')) return '/provider';
    return this.auth.hasRole('CUSTOMER') ? '/ai' : '/';
  }

  submit(form: NgForm): void {
    if (form.invalid || this.loading) {
      form.control.markAllAsTouched();
      return;
    }
    this.error = '';
    this.resetVerification();
    this.loading = true;

    if (this.mode === 'register') {
      // Cliente y proveedor reciben lo mismo: un correo con el enlace, sin sesión (HU #39)
      this.sentAsProvider = this.isProviderRegistration;
      const request = this.sentAsProvider
        ? this.auth.registerProvider(this.name, this.email, this.password)
        : this.auth.register(this.name, this.email, this.password);
      request.subscribe({
        next: sent => {
          this.loading = false;
          this.sent = sent;
          // Recién enviado: no tiene sentido pedir otro enlace en el mismo instante
          this.startCooldown();
        },
        error: (e: HttpErrorResponse) => {
          this.loading = false;
          this.error = e?.error?.detail || 'No fue posible crear tu cuenta. Inténtalo de nuevo.';
        }
      });
      return;
    }

    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl()),
      error: (e: HttpErrorResponse) => this.loginFailed(e)
    });
  }

  /**
   * Java responde 401 tanto a una contraseña incorrecta como a una cuenta que aún no verificó su correo
   * (esa cuenta todavía no existe en app_users). Para distinguirlas se pregunta al email-service.
   */
  private loginFailed(e: HttpErrorResponse): void {
    const message = e?.error?.detail || 'No fue posible autenticar. Inténtalo de nuevo.';
    if (e?.status !== 401) {
      this.loading = false;
      this.error = message;
      return;
    }
    this.auth.verificationStatus(this.email, this.password).subscribe({
      next: ({ pending }) => {
        this.loading = false;
        if (pending) this.unverified = true;
        else this.error = message;
      },
      // Si el email-service no responde, al menos se muestra el error del login
      error: () => {
        this.loading = false;
        this.error = message;
      }
    });
  }

  resend(): void {
    if (this.resending || this.resendCooldown > 0) return;
    this.resending = true;
    this.resendNotice = '';
    this.resendError = '';
    this.auth.resendVerification(this.sent?.email ?? this.email).subscribe({
      next: () => {
        this.resending = false;
        this.resendNotice = 'Te enviamos un enlace nuevo. El anterior ya no sirve.';
        this.startCooldown();
      },
      error: (e: HttpErrorResponse) => {
        this.resending = false;
        this.resendError = e?.status === 429
          ? e.error?.detail || 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.'
          : 'No pudimos reenviar el enlace. Inténtalo de nuevo.';
      }
    });
  }

  private resetVerification(): void {
    this.sent = null;
    this.unverified = false;
    this.resendNotice = '';
    this.resendError = '';
    this.stopCooldown();
  }

  private startCooldown(): void {
    this.stopCooldown();
    this.resendCooldown = RESEND_COOLDOWN_S;
    this.cooldownTimer = setInterval(() => {
      if (--this.resendCooldown <= 0) this.stopCooldown();
    }, 1000);
  }

  private stopCooldown(): void {
    clearInterval(this.cooldownTimer);
    this.cooldownTimer = undefined;
    this.resendCooldown = 0;
  }
}
