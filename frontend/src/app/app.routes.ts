import { CanActivateFn, Routes, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './core/auth.service';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';

// IMPORTA TUS NUEVOS COMPONENTES
import { ProviderOfferingsComponent } from './features/provider-offerings.component';

const authenticatedOnly: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

// NUEVO GUARD: Solo deja pasar si está autenticado Y es proveedor
const providerOnly: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  // Revisa que tenga el rol (ajusta auth.hasRole según cómo lo tengas en tu AuthService)
  return (auth.isAuthenticated() && auth.hasRole('PROVIDER')) ? true : router.createUrlTree(['/']);
};

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'ai', component: AiComponent },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [authenticatedOnly] },
  { path: 'book', component: BookingComponent },
  
  // NUEVAS RUTAS PROTEGIDAS PARA EL PROVEEDOR
  { path: 'provider/offerings', component: ProviderOfferingsComponent, canActivate: [providerOnly] },
  
  { path: '**', redirectTo: '' }
];