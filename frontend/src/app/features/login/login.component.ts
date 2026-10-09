import { Component } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  mode: 'login' | 'register' = 'login';
  accountType: 'CUSTOMER' | 'PROVIDER' = 'CUSTOMER';
  name = ''; email = ''; password = '';
  // Mensaje del backend tras enviar una solicitud de proveedor; mientras exista se muestra la confirmación
  providerRequestMessage = '';
  error = '';
  loading = false;
  showPassword = false;
  readonly year = new Date().getFullYear();

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {
    // ?mode=register abre el registro (botón "Crear cuenta" del header); ?type=provider lo abre como proveedor
    const params = this.route.snapshot.queryParamMap;
    if (params.get('mode') === 'register') this.mode = 'register';
    if (params.get('type') === 'provider') { this.mode = 'register'; this.accountType = 'PROVIDER'; }
  }

  toggle(form: NgForm): void {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.error = '';
    this.providerRequestMessage = '';
    form.resetForm({ name: '', email: this.email, password: '' });
  }

  get isProviderRegistration(): boolean { return this.mode === 'register' && this.accountType === 'PROVIDER'; }

  backToLogin(): void {
    this.providerRequestMessage = '';
    this.mode = 'login';
    this.password = '';
  }

  // Solo se aceptan rutas internas para evitar redirecciones abiertas (p. ej. returnUrl=//sitio-malicioso.com)
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    if (url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')) return url;
    return this.auth.hasRole('CUSTOMER') ? '/ai' : '/';
  }

  submit(form: NgForm): void {
    if (form.invalid || this.loading) {
      form.control.markAllAsTouched();
      return;
    }
    this.error = '';
    this.loading = true;

    if (this.isProviderRegistration) {
      this.auth.registerProvider(this.name, this.email, this.password).subscribe({
        next: r => {
          this.loading = false;
          this.providerRequestMessage = r.message;
        },
        error: e => {
          this.loading = false;
          this.error = e?.error?.detail || 'No fue posible enviar tu solicitud. Inténtalo de nuevo.';
        }
      });
      return;
    }

    const request = this.mode === 'login'
      ? this.auth.login(this.email, this.password)
      : this.auth.register(this.name, this.email, this.password);
    request.subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl()),
      error: e => {
        this.loading = false;
        this.error = e?.error?.detail || 'No fue posible autenticar. Inténtalo de nuevo.';
      }
    });
  }
}
