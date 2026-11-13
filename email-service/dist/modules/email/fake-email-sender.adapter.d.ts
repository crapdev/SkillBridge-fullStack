import { EmailMessage, EmailSenderPort } from './email-sender.port';
export declare class FakeEmailSenderAdapter implements EmailSenderPort {
    private readonly logger;
    send(message: EmailMessage): Promise<void>;
}
