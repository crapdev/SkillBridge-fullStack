import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}
  
  // Catálogo público para los estudiantes
  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }

  // --- MÉTODOS EXCLUSIVOS DEL PROVEEDOR ---
  
  getProviderOfferings() {
    return this.http.get<Offering[]>(`${apiBase()}/provider/offerings`);
  }

  createProviderOffering(data: any) {
    return this.http.post<Offering>(`${apiBase()}/provider/offerings`, data);
  }

  deleteProviderOffering(id: string) {
    return this.http.delete(`${apiBase()}/provider/offerings/${id}`);
  }
}