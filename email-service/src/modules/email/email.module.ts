import { Module } from '@nestjs/common';
import { EmailSenderPort } from './email-sender.port';
import { FakeEmailSenderAdapter } from './fake-email-sender.adapter';

@Module({
  providers: [
    FakeEmailSenderAdapter,
    { provide: EmailSenderPort, useExisting: FakeEmailSenderAdapter }
  ],
  exports: [EmailSenderPort]
})
export class EmailModule {}
