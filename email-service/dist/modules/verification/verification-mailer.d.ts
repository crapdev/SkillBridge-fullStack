import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { VerificationMail, VerificationMailerPort } from './verification-mailer.port';
export declare class VerificationMailer implements VerificationMailerPort {
    private readonly configService;
    private readonly emailSender;
    constructor(configService: ConfigService, emailSender: EmailSenderPort);
    sendVerification(mail: VerificationMail): Promise<void>;
}
