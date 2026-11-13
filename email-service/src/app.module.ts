import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import * as Joi from 'joi';
import { EmailModule } from './modules/email/email.module';
import { BookingEventsModule } from './modules/booking-events/booking-events.module';
import { HealthModule } from './modules/health/health.module';
import { DatabaseModule } from './modules/database/database.module';
import { VerificationModule } from './modules/verification/verification.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
      validationSchema: Joi.object({
        NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
        PORT: Joi.number().default(3000),
        APP_NAME: Joi.string().default('skillbridge-email-service'),
        RABBITMQ_URL: Joi.string().uri().optional(),
        RABBITMQ_EXCHANGE: Joi.string().default('booking.events'),
        RABBITMQ_EMAIL_QUEUE: Joi.string().default('booking.emails.queue'),
        RABBITMQ_EMAIL_DLQ: Joi.string().default('booking.emails.dlq'),
        MAIL_PROVIDER: Joi.string().valid('fake', 'mailpit', 'brevo').default('fake'),
        MAIL_FROM: Joi.string().email().default('no-reply@skillbridge.local'),
        MAILPIT_HOST: Joi.string().default('localhost'),
        MAILPIT_PORT: Joi.number().default(1025),
        BREVO_API_KEY: Joi.string().allow('').optional(),
        FRONTEND_URL: Joi.string().uri().default('http://localhost:4200'),
        DATABASE_URL: Joi.string().uri({ scheme: ['postgres', 'postgresql'] }).required()
      })
    }),
    EmailModule,
    BookingEventsModule,
    HealthModule,
    DatabaseModule,
    VerificationModule
  ]
})
export class AppModule {}
