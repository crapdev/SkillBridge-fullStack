/**
 * Ícono de cada categoría de servicio.
 * Desde HU-20 el temario, el nivel y la duración los define el proveedor en su mentoría
 * (llegan del backend en Offering); aquí ya no hay contenido fijo.
 */
export type CategoryKey = 'cloud' | 'frontend' | 'backend';

export interface ServiceContent {
  icon: string;
}

export const SERVICE_CONTENT: Record<CategoryKey, ServiceContent> = {
  cloud: { icon: 'icons/CLOUD.png' },
  frontend: { icon: 'icons/FRONTEND.png' },
  backend: { icon: 'icons/BACKEND.png' }
};

export function categoryKey(category: string): CategoryKey {
  const key = category.toLowerCase();
  return key === 'cloud' || key === 'frontend' ? key : 'backend';
}
