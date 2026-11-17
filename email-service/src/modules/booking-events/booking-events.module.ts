import { Module } from '@nestjs/common';
import { EmailModule } from '../email/email.module';
import { BookingEmailService } from './booking-email.service';
import { BookingEventsConsumer } from './booking-events.consumer';
import { BookingRecipientsRepository } from './booking-recipients.repository';
import { EmailLogRepository } from './email-log.repository';
import { RabbitMqService } from './rabbitmq.service';

// Correos de reservas (HU #38): RabbitMQ → consumidor → datos por BD → plantilla → EmailSenderPort
@Module({
  imports: [EmailModule],
  providers: [
    BookingRecipientsRepository,
    EmailLogRepository,
    BookingEmailService,
    BookingEventsConsumer,
    RabbitMqService,
  ],
})
export class BookingEventsModule {}
