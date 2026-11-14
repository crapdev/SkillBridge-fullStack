import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import * as Joi from 'joi';
import { DatabaseModule } from './database/database.module';
import { EmailModule } from './modules/email/email.module';
import { BookingEventsModule } from './modules/booking-events/booking-events.module';
import { HealthModule } from './modules/health/health.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
      validationSchema: Joi.object({
        NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
        PORT: Joi.number().default(3000),
        APP_NAME: Joi.string().default('skillbridge-email-service'),
        RABBITMQ_URL: Joi.string().uri({ scheme: ['amqp', 'amqps'] }).allow('').optional(),
        RABBITMQ_EXCHANGE: Joi.string().default('booking.events'),
        RABBITMQ_EMAIL_QUEUE: Joi.string().default('booking.emails.queue'),
        RABBITMQ_EMAIL_DLQ: Joi.string().default('booking.emails.dlq'),
        RABBITMQ_EMAIL_DLX: Joi.string().default('booking.emails.dlx'),
        RABBITMQ_EMAIL_RETRY_QUEUE: Joi.string().default('booking.emails.retry.queue'),
        RABBITMQ_PREFETCH: Joi.number().integer().min(1).max(100).default(5),
        BOOKING_EMAIL_MAX_ATTEMPTS: Joi.number().integer().min(1).max(20).default(5),
        BOOKING_EMAIL_RETRY_DELAY_MS: Joi.number().integer().min(1000).default(30000),
        MAIL_PROVIDER: Joi.string().valid('fake', 'mailpit', 'brevo').default('fake'),
        MAIL_FROM: Joi.string().email({ tlds: false }).default('no-reply@skillbridge.local'),
        MAILPIT_HOST: Joi.string().default('localhost'),
        MAILPIT_PORT: Joi.number().default(1025),
        BREVO_API_KEY: Joi.string().allow('').optional(),
        FRONTEND_URL: Joi.string().uri().default('http://localhost:4200'),
        // Contexto de reservas (endpoint interno de Java, pendiente). Sin URL el consumidor no arranca.
        BOOKING_CONTEXT_URL: Joi.string().uri({ scheme: ['http', 'https'] }).allow('').optional(),
        INTERNAL_API_TOKEN: Joi.string().allow('').optional(),
        BOOKING_CONTEXT_TIMEOUT_MS: Joi.number().integer().min(100).default(5000),
        DATABASE_URL: Joi.string().default('postgres://skillbridge:skillbridge_dev@localhost:5432/skillbridge'),
        DATABASE_SSL: Joi.string().valid('true', 'false').default('false'),
        // Tiempo que un envío queda reservado (PROCESSING) antes de que otro intento pueda retomarlo
        BOOKING_EMAIL_LEASE_SECONDS: Joi.number().integer().min(10).default(120)
      })
    }),
    DatabaseModule,
    EmailModule,
    BookingEventsModule,
    HealthModule
  ]
})
export class AppModule {}
