import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { RevealDirective } from '../../shared/reveal.directive';

export interface TeamMember {
  name: string;
  role: string;
  badge: string;
  initials: string;
  bio: string;
  tags: string[];
  isLead?: boolean;
}

export interface ValueItem {
  icon: string;
  title: string;
  badge: string;
  text: string;
}

export interface WorkflowItem {
  step: string;
  badge: string;
  title: string;
  description: string;
  details: string[];
  icon: string;
}

@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective],
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.css']
})
export class AboutComponent {
  readonly auth = inject(AuthService);

  showLegalModal = false;
  activeLegalTab: 'terms' | 'privacy' = 'terms';

  openLegalModal(tab: 'terms' | 'privacy' = 'terms'): void {
    this.activeLegalTab = tab;
    this.showLegalModal = true;
  }

  closeLegalModal(): void {
    this.showLegalModal = false;
  }

  // Métricas destacadas del Hero
  readonly platformStats = [
    { value: '1:1', label: 'Sesiones en vivo', note: 'Casos reales, no videos pregrabados' },
    { value: '3', label: 'Especialidades', note: 'Backend, Frontend y Cloud' },
    { value: '100%', label: 'Mentores verificados', note: 'Aprobados por el equipo admin' },
    { value: '0', label: 'Fricción de agenda', note: 'Reserva automática en minutos' }
  ];

  // Misión y Visión
  readonly mission = {
    title: 'Nuestra Misión',
    lead: 'Menos tiempo buscando, más tiempo aprendiendo.',
    text: 'Construir el puente que conecta a estudiantes y desarrolladores júnior con mentores técnicos experimentados, transformando días de búsqueda solitaria en minutos de reserva 1:1 en vivo sobre casos reales, guiados por una inteligencia artificial que orienta con transparencia y jamás sustituye el valor humano.'
  };

  readonly vision = {
    title: 'Nuestra Visión',
    lead: 'Ser la red de mentoría técnica y transferencia de talento líder en Latinoamérica.',
    text: 'Consolidarnos como el canal principal donde bootcamps como Riwi y comunidades tecnológicas conecten a sus egresados sénior con los coders que vienen detrás, democratizando el acceso a buenas prácticas de arquitectura, código limpio y especialización técnica de nivel profesional.'
  };

  // Valores corporativos
  readonly values: ValueItem[] = [
    {
      badge: 'Calidad Humana',
      icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z',
      title: 'Aprendizaje 1:1 y en vivo',
      text: 'Creemos en personas reales resolviendo dudas reales. No videos de hace dos años ni cursos genéricos de 40 horas: una hora intensiva con quien domina tu tecnología.'
    },
    {
      badge: 'Ética Digital',
      icon: 'M13 10V3L4 14h7v7l9-11h-7z',
      title: 'IA que guía, no que reemplaza',
      text: 'Nuestro asistente orienta tu camino sugiriendo mentorías exactas del catálogo según tus metas. No inventa cursos y protege tu privacidad sin enviar datos personales.'
    },
    {
      badge: 'Rigor Técnico',
      icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z',
      title: 'Mentores verificados',
      text: 'No somos un marketplace genérico de todo tipo de servicios. Un administrador valida y aprueba cada perfil antes de que pueda publicar sus horarios.'
    },
    {
      badge: 'Transparencia',
      icon: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
      title: 'Cero fricción y reglas claras',
      text: 'Adiós a negociar precios y horarios por chat. Agendas automatizadas sin cruces, tarifas transparentes (10% de comisión), pagos seguros en Stripe y reembolsos justos.'
    }
  ];

  // Funcionamiento: Mentorías, Revisión y Pagos
  readonly workflows: WorkflowItem[] = [
    {
      step: '01',
      badge: 'Mentorías 1:1',
      title: '¿Cómo funcionan las mentorías?',
      description: 'De la duda a la solución en minutos, enfocado en tu necesidad puntual:',
      details: [
        'Especialización técnica: sesiones directas en Backend (Spring Boot), Frontend (Angular) o Cloud (Docker, Nginx).',
        'Agenda sin chat: selecciona fecha y hora disponible en el calendario del mentor con reserva inmediata.',
        'Sesión en vivo: 60 minutos prácticos sobre tu código, arquitectura o proyecto, resolviendo bloqueos reales.'
      ],
      icon: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z'
    },
    {
      step: '02',
      badge: 'Verificación',
      title: '¿Cómo es la revisión de mentorías?',
      description: 'Control de calidad estricto para asegurar que solo enseñe gente capacitada:',
      details: [
        'Aprobación de cuentas: el administrador revisa el perfil y la experiencia técnica del mentor antes de activarlo.',
        'Ofertas claras: cada mentoría detalla temario, nivel, duración y precio antes de que pagues.',
        'Asistente de IA: evalúa tu meta profesional y te orienta hacia la mentoría adecuada sin que gastes en algo que aún no necesitas.'
      ],
      icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z'
    },
    {
      step: '03',
      badge: 'Finanzas Claras',
      title: '¿Cómo funcionan los pagos y reembolsos?',
      description: 'Seguridad bancaria mediante Stripe y condiciones justas para ambas partes:',
      details: [
        'Pasarela Stripe: procesa tus pagos con tarjeta de forma cifrada sin que guardemos datos financieros.',
        'Comisión clara del 10%: el proveedor recibe pagos semanales de sus sesiones acumuladas.',
        'Garantía de reembolso: cancelación total con más de 24 horas de anticipación o si el mentor no asiste a la sesión.'
      ],
      icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z'
    }
  ];

  // Integrantes del equipo
  readonly team: TeamMember[] = [
    {
      name: 'Cristian Albor',
      role: 'Team Lead & Scrum Master',
      badge: 'Liderazgo & Scrum',
      initials: 'CA',
      isLead: true,
      bio: 'Liderazgo ágil del sprint, orquestación del equipo integrador, definición de arquitectura y aseguramiento de entrega de valor continua.',
      tags: ['Scrum Master', 'Arquitectura', 'Sprint Planning', 'Gestión Técnica']
    },
    {
      name: 'Isaac Guzman',
      role: 'QA & DevOps Engineer',
      badge: 'QA · DevOps',
      initials: 'IG',
      isLead: true,
      bio: 'Automatización de pipelines CI/CD, despliegue con Docker y Nginx, aseguramiento de calidad con pruebas continuas e infraestructura en la nube.',
      tags: ['Docker', 'Nginx', 'CI/CD', 'Testing E2E', 'Linux']
    },
    {
      name: 'Daniel Viaña',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'DV',
      bio: 'Desarrollo de servicios de negocio en Spring Boot, diseño de endpoints REST, integración reactiva en Angular y modelado de datos.',
      tags: ['Java 21', 'Spring Boot', 'Angular', 'PostgreSQL']
    },
    {
      name: 'Andres Quintero',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'AQ',
      bio: 'Implementación de arquitectura hexagonal, persistencia con JPA/Flyway, autenticación segura y componentes de interfaz de usuario.',
      tags: ['Spring Security', 'JWT', 'TypeScript', 'Flyway']
    },
    {
      name: 'Camilo Ramirez',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'CR',
      bio: 'Gestión de flujos de reserva, lógica de calendario en tiempo real, manejo reactivo de estado con signals y comunicación API.',
      tags: ['Angular Signals', 'RxJS', 'Spring Boot', 'REST APIs']
    },
    {
      name: 'Leonardo Jimenez',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'LJ',
      bio: 'Construcción del catálogo de mentorías, filtros por categorías, módulos interactivos de cliente y optimización del rendimiento frontend.',
      tags: ['Angular 20', 'CSS Moderno', 'PostgreSQL', 'UI/UX']
    },
    {
      name: 'Carlos Ospina',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'CO',
      bio: 'Lógica transaccional para pasarela de pagos, manejo de eventos asíncronos con RabbitMQ y diseño de paneles administrativos.',
      tags: ['Stripe API', 'RabbitMQ', 'Spring Boot', 'Angular']
    },
    {
      name: 'David Carrascal',
      role: 'Desarrollador Full Stack',
      badge: 'Full Stack',
      initials: 'DC',
      bio: 'Integración del motor de recomendaciones con inteligencia artificial, validación de formularios y diseño centrado en el usuario.',
      tags: ['Motor IA', 'Spring Boot', 'TypeScript', 'Accesibilidad']
    }
  ];

  readonly pillars = [
    {
      icon: 'M4 21a8 8 0 0 1 16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
      title: 'Mentorías 1:1 en vivo',
      text: 'Sesiones virtuales con especialistas en backend, frontend y cloud, enfocadas en resolver tu bloqueo real.'
    },
    {
      icon: 'M8 9l-4 3 4 3M16 9l4 3-4 3M13.5 6l-3 12',
      title: 'Revisión y feedback técnico',
      text: 'Analizamos tu código o arquitectura y te entregamos recomendaciones concretas listas para aplicar.'
    },
    {
      icon: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z',
      title: 'Recomendaciones con IA',
      text: 'Un asistente que orienta tu objetivo profesional sugiriendo solo mentorías verificadas de nuestro catálogo.'
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
    { group: 'Frontend', items: ['Angular 20', 'Signals', 'Nginx'] },
    { group: 'Operación', items: ['Docker', 'GitHub Actions', 'Prometheus', 'Grafana'] }
  ];
}
