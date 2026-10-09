import { Component, OnInit } from '@angular/core';
import { Offering, OfferingService } from '../../core/offering.service';
import { RouterLink } from '@angular/router';
import { ServiceCardComponent } from '../../shared/service-card/service-card.component';

@Component({
  standalone: true,
  imports: [RouterLink, ServiceCardComponent],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {
  // El inicio muestra solo una vista previa; el catálogo completo vive en /servicios
  readonly previewSize = 3;
  offerings: Offering[] = [];
  error = '';
  constructor(private service: OfferingService) {}
  ngOnInit(): void { this.service.list().subscribe({ next: r => this.offerings = r, error: () => this.error = 'No fue posible cargar el catálogo.' }); }
}
