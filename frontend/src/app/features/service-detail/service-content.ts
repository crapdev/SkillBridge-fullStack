/**
 * Contenido editorial del detalle de cada servicio, agrupado por categoría.
 * El backend solo entrega título, descripción, categoría y precio; lo que se muestra
 * además (temas, nivel, duración) se ajusta aquí sin tocar la API.
 */
export type CategoryKey = 'cloud' | 'frontend' | 'backend';

export interface ServiceContent {
  icon: string;
  level: string;
  duration: string;
  topics: { title: string; text: string }[];
}

export const SERVICE_CONTENT: Record<CategoryKey, ServiceContent> = {
  cloud: {
    icon: 'icons/CLOUD.png',
    level: 'Intermedio · Avanzado',
    duration: '60 minutos',
    topics: [
      { title: 'Contenedores', text: 'Imágenes con Docker y entornos reproducibles con Compose.' },
      { title: 'Arquitectura distribuida', text: 'Separación de servicios, límites y comunicación entre ellos.' },
      { title: 'Mensajería y eventos', text: 'Flujos asíncronos con RabbitMQ y manejo de reintentos.' },
      { title: 'Caché y rendimiento', text: 'Dónde usar Redis y cómo invalidar sin romper datos.' },
      { title: 'Observabilidad', text: 'Health checks, métricas y logs que sirven en producción.' },
      { title: 'Despliegue', text: 'Variables de entorno, secretos y estrategias de publicación.' }
    ]
  },
  frontend: {
    icon: 'icons/FRONTEND.png',
    level: 'Básico · Intermedio',
    duration: '60 minutos',
    topics: [
      { title: 'Componentes modernos', text: 'Standalone components, signals y control flow.' },
      { title: 'RxJS sin fugas', text: 'Operadores clave y suscripciones bien gestionadas.' },
      { title: 'Arquitectura', text: 'Estructura de carpetas, servicios y estado de la app.' },
      { title: 'Rutas y seguridad', text: 'Guards, interceptores y manejo de sesión.' },
      { title: 'Formularios', text: 'Validaciones claras y buena experiencia de usuario.' },
      { title: 'Rendimiento y accesibilidad', text: 'Carga diferida, buenas prácticas y a11y.' }
    ]
  },
  backend: {
    icon: 'icons/BACKEND.png',
    level: 'Intermedio',
    duration: '60 minutos',
    topics: [
      { title: 'Arquitectura hexagonal', text: 'Puertos, adaptadores y un dominio independiente.' },
      { title: 'APIs REST', text: 'Diseño de endpoints y contratos con Spring Boot.' },
      { title: 'Seguridad', text: 'Spring Security, JWT y control de acceso por roles.' },
      { title: 'Persistencia', text: 'JPA, consultas eficientes y migraciones con Flyway.' },
      { title: 'Manejo de errores', text: 'Respuestas consistentes con ProblemDetail.' },
      { title: 'Pruebas', text: 'Tests unitarios y de integración que dan confianza.' }
    ]
  }
};

export function categoryKey(category: string): CategoryKey {
  const key = category.toLowerCase();
  return key === 'cloud' || key === 'frontend' ? key : 'backend';
}
