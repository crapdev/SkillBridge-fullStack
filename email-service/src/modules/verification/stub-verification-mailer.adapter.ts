import { Injectable, Logger } from '@nestjs/common';
import { VerificationMail, VerificationMailerPort } from './verification-mailer.port';

@Injectable()
export class StubVerificationMailer implements VerificationMailerPort {
  private readonly logger = new Logger(StubVerificationMailer.name);

  async sendVerification(_mail: VerificationMail): Promise<void> {
    this.logger.log('Correo de verificación pendiente de implementar');
  }
}
