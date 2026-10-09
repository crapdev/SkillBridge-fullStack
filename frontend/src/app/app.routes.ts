import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AiComponent } from './features/ai/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings/my-bookings.component';
import { PaymentComponent } from './features/payment/payment.component';
import { ServiceDetailComponent } from './features/service-detail/service-detail.component';
import { AboutComponent } from './features/about/about.component';
import { ServicesComponent } from './features/services/services.component';
import { ProviderOfferingsComponent } from './features/provider-offerings.component';
import { ProviderSlotsComponent } from './features/provider-slots/provider-slots.component';
import { adminGuard, customerGuard, providerGuard } from './core/role.guard';
import { guestGuard } from './core/guest.guard';

// El título fijo evita que la pestaña conserve el del panel al volver al sitio público
const title = 'SkillBridge AI';

export const routes: Routes = [
  { path: '', component: HomeComponent, title },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard], title },
  { path: 'servicios', component: ServicesComponent, title },
  { path: 'services/:id', component: ServiceDetailComponent, title },
  { path: 'sobre-nosotros', component: AboutComponent, title },
  { path: 'ai', component: AiComponent, canActivate: [customerGuard], title },
  { path: 'book', component: BookingComponent, canActivate: [customerGuard], title },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [customerGuard], title },
  { path: 'bookings/:id/pay', component: PaymentComponent, canActivate: [customerGuard], title },
  { path: 'my-bookings', redirectTo: 'bookings/me' },
  { path: 'provider/offerings', component: ProviderOfferingsComponent, canActivate: [providerGuard], title },
  { path: 'provider/offerings/:id/horarios', component: ProviderSlotsComponent, canActivate: [providerGuard], title },
  // Panel de administración: se carga bajo demanda y solo para el rol ADMIN
  {
    path: 'admin',
    canActivate: [adminGuard],
    canActivateChild: [adminGuard],
    loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES)
  },
  { path: '**', redirectTo: '' }
];
