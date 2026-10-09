import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { Offering, OfferingService } from '../../core/offering.service';
import { RouterLink } from '@angular/router';
import { RevealDirective } from '../../shared/reveal.directive';
import { ServiceCardComponent } from '../../shared/service-card/service-card.component';
import { AboutTeaserComponent } from './sections/about-teaser.component';
import { BecomeMentorComponent } from './sections/become-mentor.component';
import { FinalCtaComponent } from './sections/final-cta.component';
import { HowItWorksComponent } from './sections/how-it-works.component';

@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective, ServiceCardComponent, HowItWorksComponent, AboutTeaserComponent, BecomeMentorComponent, FinalCtaComponent],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {
  // El inicio muestra solo una vista previa; el catálogo completo vive en /servicios
  readonly previewSize = 3;
  readonly auth = inject(AuthService);
  offerings: Offering[] = [];
  error = '';

  get categoryCount(): number { return new Set(this.offerings.map(o => o.category)).size; }

  constructor(private service: OfferingService) {}
  ngOnInit(): void { this.service.list().subscribe({ next: r => this.offerings = r, error: () => this.error = 'No fue posible cargar el catálogo.' }); }
}
