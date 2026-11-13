import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, VerificationError } from '../../core/auth.service';

/** Estados de la pantalla: uno por cada respuesta posible del backend, más "verificando" y "fallo de red". */
type State = 'verifying' | 'provider' | 'expired' | 'used' | 'invalid' | 'failed';

/**
 * Destino del enlace del correo de verificación (HU #39): /verificar-correo?token=...
 * El cliente verificado entra directo a /ai; el proveedor queda pendiente de que un admin lo apruebe.
 */
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './verify-email.component.html',
  // Mismo aspecto que el login: se reutilizan sus estilos en lugar de copiarlos
  styleUrls: ['../login/login.component.css', './verify-email.component.css']
})
export class VerifyEmailComponent implements OnInit {
  state: State = 'verifying';
  providerMessage = '';
  // Formulario para pedir otro enlace cuando el actual venció o no es válido
  email = '';
  resending = false;
  resendNotice = '';
  resendError = '';
  private token = '';

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    // Sin token no hay nada que preguntar al backend: el enlace llegó roto
    if (this.token) this.verify();
    else this.state = 'invalid';
  }

  verify(): void {
    this.state = 'verifying';
    this.auth.verifyEmail(this.token).subscribe({
      next: result => {
        if (result.kind === 'customer') {
          // replaceUrl: el botón "atrás" no vuelve a esta página con un enlace que ya se usó
          this.router.navigateByUrl('/ai', { replaceUrl: true });
        } else {
          this.providerMessage = result.message;
          this.state = 'provider';
        }
      },
      error: (e: HttpErrorResponse) => {
        const reason = e?.error?.reason as VerificationError | undefined;
        if (e?.status !== 400 || !reason) { this.state = 'failed'; return; }
        this.state = reason === 'TOKEN_EXPIRED' ? 'expired' : reason === 'TOKEN_USED' ? 'used' : 'invalid';
      }
    });
  }

  resend(form: NgForm): void {
    if (form.invalid || this.resending) {
      form.control.markAllAsTouched();
      return;
    }
    this.resending = true;
    this.resendError = '';
    this.auth.resendVerification(this.email).subscribe({
      next: () => {
        this.resending = false;
        // El backend responde igual exista o no el correo, así que el texto tampoco lo afirma
        this.resendNotice = 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo. Revisa tu bandeja de entrada.';
      },
      error: (e: HttpErrorResponse) => {
        this.resending = false;
        this.resendError = e?.status === 429
          ? e.error?.detail || 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.'
          : 'No pudimos reenviar el enlace. Inténtalo de nuevo.';
      }
    });
  }
}
