import { Routes } from '@angular/router';
import { ProviderLayoutComponent } from './provider-layout.component';
import { ProviderDashboardComponent } from './provider-dashboard.component';
import { ProviderOfferingsComponent } from '../provider-offerings.component';
import { ProviderSlotsComponent } from '../provider-slots/provider-slots.component';

/** Panel del proveedor (HU-22): las URLs de mentorías y horarios no cambian, ahora viven dentro del layout */
export const PROVIDER_ROUTES: Routes = [
  {
    path: '',
    component: ProviderLayoutComponent,
    children: [
      { path: '', component: ProviderDashboardComponent, title: 'Dashboard · SkillBridge Mentor' },
      { path: 'offerings', component: ProviderOfferingsComponent, title: 'Mis mentorías · SkillBridge Mentor' },
      { path: 'offerings/:id/horarios', component: ProviderSlotsComponent, title: 'Horarios · SkillBridge Mentor' },
      { path: '**', redirectTo: '' }
    ]
  }
];
