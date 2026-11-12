export interface EmailMessage {
  to: string;
  recipientName: string;
  subject: string;
  html: string;
  text: string;
}

export abstract class EmailSenderPort {
  abstract send(message: EmailMessage): Promise<void>;
}
