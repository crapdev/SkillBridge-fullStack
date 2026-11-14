import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DataSource } from 'typeorm';
import { EmailSenderPort } from '../email/email-sender.port';
import { EmailModule } from '../email/email.module';
import { RabbitMqModule } from '../rabbitmq/rabbitmq.module';
import { RabbitMqService } from '../rabbitmq/rabbitmq.service';
import { BookingEmailProcessor } from './booking-email.processor';
import { BOOKING_EVENTS_CONFIG, BookingEventsConfig, bookingEventsConfigFactory } from './booking-events.config';
import { BookingEventsConsumer } from './booking-events.consumer';
import { BookingContextPort } from './context/booking-context.port';
import { HttpBookingContextAdapter } from './context/http-booking-context.adapter';
import { DeliveryLedgerPort } from './idempotency/delivery-ledger.port';
import { PostgresDeliveryLedger } from './idempotency/postgres-delivery-ledger';

/**
 * Correos de reservas (Persona B): consume booking.emails.queue y envía con el EmailSenderPort de Persona A.
 * Requiere el DatabaseModule (TypeORM) importado en AppModule para el registro de entregas.
 */
@Module({
  imports: [RabbitMqModule, EmailModule],
  providers: [
    { provide: BOOKING_EVENTS_CONFIG, inject: [ConfigService], useFactory: bookingEventsConfigFactory },
    {
      provide: BookingContextPort,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        new HttpBookingContextAdapter({
          baseUrl: config.get<string>('BOOKING_CONTEXT_URL') ?? '',
          token: config.get<string>('INTERNAL_API_TOKEN') || undefined,
          timeoutMs: Number(config.getOrThrow('BOOKING_CONTEXT_TIMEOUT_MS'))
        })
    },
    {
      provide: DeliveryLedgerPort,
      inject: [DataSource, ConfigService],
      useFactory: (ds: DataSource, config: ConfigService) =>
        new PostgresDeliveryLedger(ds, Number(config.getOrThrow('BOOKING_EMAIL_LEASE_SECONDS')))
    },
    {
      provide: BookingEmailProcessor,
      inject: [BookingContextPort, DeliveryLedgerPort, EmailSenderPort, ConfigService],
      useFactory: (context: BookingContextPort, ledger: DeliveryLedgerPort, sender: EmailSenderPort, config: ConfigService) =>
        new BookingEmailProcessor(context, ledger, sender, config.getOrThrow<string>('FRONTEND_URL'))
    },
    {
      provide: BookingEventsConsumer,
      inject: [RabbitMqService, BookingEmailProcessor, BOOKING_EVENTS_CONFIG, ConfigService],
      useFactory: (rabbit: RabbitMqService, processor: BookingEmailProcessor, cfg: BookingEventsConfig, config: ConfigService) =>
        new BookingEventsConsumer(rabbit, processor, cfg, Boolean(config.get<string>('BOOKING_CONTEXT_URL')))
    }
  ]
})
export class BookingEventsModule {}
