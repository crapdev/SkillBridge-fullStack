import { Injectable, Logger } from '@nestjs/common';
import { EmailMessage, EmailSenderPort } from './email-sender.port';

@Injectable()
export class FakeEmailSenderAdapter implements EmailSenderPort {
  private readonly logger = new Logger(FakeEmailSenderAdapter.name);

  async send(message: EmailMessage): Promise<void> {
    const domain = message.to.split('@')[1] ?? 'invalid';
    this.logger.log(`FAKE email accepted; subject="${message.subject}", recipientDomain="${domain}"`);
  }
}
