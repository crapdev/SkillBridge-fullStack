export interface VerificationMail {
  to: string;
  name: string;
  token: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

export abstract class VerificationMailerPort {
  abstract sendVerification(mail: VerificationMail): Promise<void>;
}
