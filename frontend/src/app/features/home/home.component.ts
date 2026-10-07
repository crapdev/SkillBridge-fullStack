import { Component, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Offering, OfferingService } from '../../core/offering.service';
import { RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {
  offerings: Offering[] = [];
  error = '';
  constructor(private service: OfferingService) {}
  ngOnInit(): void { this.service.list().subscribe({ next: r => this.offerings = r, error: () => this.error = 'No fue posible cargar el catálogo.' }); }
}
