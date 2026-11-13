import { DataSource } from 'typeorm';
import { VerificationMailerPort } from './verification-mailer.port';
export declare class VerificationSupportService {
    private readonly dataSource;
    private readonly verificationMailer;
    private readonly logger;
    private readonly dummyPasswordHash;
    constructor(dataSource: DataSource, verificationMailer: VerificationMailerPort);
    resend(rawEmail: string): Promise<{
        message: string;
    }>;
    status(rawEmail: string, password: string): Promise<{
        pending: boolean;
    }>;
    private normalizeEmail;
}
