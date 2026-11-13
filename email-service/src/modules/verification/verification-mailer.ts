import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { VerificationMail, VerificationMailerPort } from './verification-mailer.port';
import { buildVerificationEmailTemplate } from './templates/verification-email.template';

@Injectable()
export class VerificationMailer implements VerificationMailerPort {
  constructor(
    private readonly configService: ConfigService,
    private readonly emailSender: EmailSenderPort,
  ) {}

  async sendVerification(mail: VerificationMail): Promise<void> {
    const frontendUrl = this.configService.get<string>('FRONTEND_URL', 'http://localhost:4200');
    const verifyUrl = `${frontendUrl.replace(/\/$/, '')}/verificar-correo?token=${encodeURIComponent(mail.token)}`;
    const template = buildVerificationEmailTemplate({
      name: mail.name,
      verifyUrl,
      role: mail.role,
    });

    await this.emailSender.send({
      to: mail.to,
      recipientName: mail.name,
      subject: template.subject,
      html: template.html,
      text: template.text,
    });
  }
}
