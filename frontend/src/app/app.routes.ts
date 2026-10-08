import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';
import { customerGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'ai', component: AiComponent, canActivate: [customerGuard] },
  { path: 'book', component: BookingComponent, canActivate: [customerGuard] },
  { path: 'my-bookings', component: MyBookingsComponent, canActivate: [customerGuard] },
  { path: '**', redirectTo: '' }
];
