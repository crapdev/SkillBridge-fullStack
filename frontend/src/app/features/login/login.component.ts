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
  name = ''; email = ''; password = '';
  error = '';
  loading = false;
  showPassword = false;
  readonly year = new Date().getFullYear();

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {
    // El botón "Crear cuenta" del header abre directamente el formulario de registro
    if (this.route.snapshot.queryParamMap.get('mode') === 'register') this.mode = 'register';
  }

  toggle(form: NgForm): void {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.error = '';
    form.resetForm({ name: '', email: this.email, password: '' });
  }

  // Solo se aceptan rutas internas para evitar redirecciones abiertas (p. ej. returnUrl=//sitio-malicioso.com)
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    if (url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')) return url;
    if (this.auth.hasRole('ADMIN')) return '/admin';
    return this.auth.hasRole('CUSTOMER') ? '/ai' : '/';
  }

  submit(form: NgForm): void {
    if (form.invalid || this.loading) {
      form.control.markAllAsTouched();
      return;
    }
    this.error = '';
    this.loading = true;
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
