import { ConfigService } from '@nestjs/config';

/** Configuración del consumidor de reservas, leída una sola vez del entorno ya validado por Joi. */
export interface BookingEventsConfig {
  exchange: string;
  queue: string;
  dlx: string;
  dlq: string;
  retryQueue: string;
  prefetch: number;
  maxAttempts: number;
  retryDelayMs: number;
}

export const BOOKING_EVENTS_CONFIG = Symbol('BOOKING_EVENTS_CONFIG');

export function bookingEventsConfigFactory(config: ConfigService): BookingEventsConfig {
  return {
    exchange: config.getOrThrow<string>('RABBITMQ_EXCHANGE'),
    queue: config.getOrThrow<string>('RABBITMQ_EMAIL_QUEUE'),
    dlx: config.getOrThrow<string>('RABBITMQ_EMAIL_DLX'),
    dlq: config.getOrThrow<string>('RABBITMQ_EMAIL_DLQ'),
    retryQueue: config.getOrThrow<string>('RABBITMQ_EMAIL_RETRY_QUEUE'),
    prefetch: Number(config.getOrThrow('RABBITMQ_PREFETCH')),
    maxAttempts: Number(config.getOrThrow('BOOKING_EMAIL_MAX_ATTEMPTS')),
    retryDelayMs: Number(config.getOrThrow('BOOKING_EMAIL_RETRY_DELAY_MS'))
  };
}
