import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { renderVerificationEmail } from './templates/verification-email.template';
import { VerificationMail, VerificationMailerPort } from './verification-mailer.port';

// Nunca registrar en logs el token, el enlace ni el correo completo: con el enlace se activa la cuenta.
@Injectable()
export class VerificationMailer implements VerificationMailerPort {
  private readonly frontendUrl: string;

  constructor(
    private readonly sender: EmailSenderPort,
    config: ConfigService,
  ) {
    this.frontendUrl = config.getOrThrow<string>('FRONTEND_URL').replace(/\/+$/, '');
  }

  async sendVerification({ to, name, token, role }: VerificationMail): Promise<void> {
    const verifyUrl = `${this.frontendUrl}/verificar-correo?token=${encodeURIComponent(token)}`;
    const { subject, html, text } = renderVerificationEmail({ name, verifyUrl, role });
    await this.sender.send({ to, recipientName: name, subject, html, text });
  }
}
