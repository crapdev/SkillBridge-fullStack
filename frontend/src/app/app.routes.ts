import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AiComponent } from './features/ai/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';
import { ServiceDetailComponent } from './features/service-detail/service-detail.component';
import { AboutComponent } from './features/about/about.component';
import { ServicesComponent } from './features/services/services.component';
import { customerGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'servicios', component: ServicesComponent },
  { path: 'services/:id', component: ServiceDetailComponent },
  { path: 'sobre-nosotros', component: AboutComponent },
  { path: 'ai', component: AiComponent, canActivate: [customerGuard] },
  { path: 'book', component: BookingComponent, canActivate: [customerGuard] },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [customerGuard] },
  { path: 'my-bookings', redirectTo: 'bookings/me' },
  { path: '**', redirectTo: '' }
];
