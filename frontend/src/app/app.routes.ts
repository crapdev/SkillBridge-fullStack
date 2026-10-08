import { CanActivateFn, Routes, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './core/auth.service';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';

const authenticatedOnly: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'ai', component: AiComponent },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [authenticatedOnly] },
  { path: 'book', component: BookingComponent },
  { path: '**', redirectTo: '' }
];
