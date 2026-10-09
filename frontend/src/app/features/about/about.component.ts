import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { RevealDirective } from '../../shared/reveal.directive';

@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective],
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.css']
})
export class AboutComponent {
  readonly auth = inject(AuthService);

  readonly pillars = [
    {
      icon: 'M4 21a8 8 0 0 1 16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
      title: 'Mentorías 1:1',
      text: 'Sesiones en vivo con especialistas en backend, frontend y cloud, enfocadas en tu caso real.'
    },
    {
      icon: 'M8 9l-4 3 4 3M16 9l4 3-4 3M13.5 6l-3 12',
      title: 'Revisión de código',
      text: 'Analizamos tu proyecto y te entregamos recomendaciones concretas que puedes aplicar de inmediato.'
    },
    {
      icon: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z',
      title: 'Recomendaciones con IA',
      text: 'Un asistente que te sugiere servicios basándose únicamente en nuestro catálogo real.'
    }
  ];

  readonly principles = [
    { title: 'Arquitectura hexagonal', text: 'El dominio no depende de frameworks: cambiar una base de datos o un proveedor no toca las reglas de negocio.' },
    { title: 'Seguridad por diseño', text: 'Autenticación con JWT, permisos por rol y claves de IA que nunca llegan al navegador.' },
    { title: 'Calidad verificable', text: 'Pruebas automáticas, reglas de arquitectura y un pipeline de CI que valida cada cambio.' },
    { title: 'Listo para crecer', text: 'Eventos asíncronos, caché y observabilidad para evolucionar hacia una solución distribuida.' }
  ];

  readonly stack = [
    { group: 'Backend', items: ['Java 21', 'Spring Boot', 'Spring Security', 'JWT'] },
    { group: 'Datos', items: ['PostgreSQL', 'Flyway', 'Redis'] },
    { group: 'Eventos', items: ['RabbitMQ', 'Dead Letter Queue'] },
    { group: 'Frontend', items: ['Angular', 'Nginx'] },
    { group: 'Operación', items: ['Docker', 'GitHub Actions', 'Prometheus', 'Grafana'] }
  ];
}
