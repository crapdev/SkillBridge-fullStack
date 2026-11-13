import { HttpException, HttpStatus, Injectable, Logger } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { VerificationMailerPort } from './verification-mailer.port';
import { hashToken, newVerificationToken } from './token.util';

const RESEND_LIMIT = 3;
const RESEND_WINDOW_MINUTES = 15;
const VERIFICATION_TOKEN_TTL_HOURS = 24;
const DUMMY_PASSWORD = 'skillbridge-dummy-password-never-used';

interface PendingRegistrationRow {
  id: string;
  name: string;
  password_hash: string;
  role: 'CUSTOMER' | 'PROVIDER';
}

@Injectable()
export class VerificationSupportService {
  private readonly logger = new Logger(VerificationSupportService.name);
  // Start this once per service instance so unknown emails still perform one bcrypt comparison.
  private readonly dummyPasswordHash = bcrypt.hash(DUMMY_PASSWORD, 10);

  constructor(
    @InjectDataSource() private readonly dataSource: DataSource,
    private readonly verificationMailer: VerificationMailerPort,
  ) {}

  async resend(rawEmail: string): Promise<{ message: string }> {
    const email = this.normalizeEmail(rawEmail);

    const pending = await this.dataSource.transaction(async (manager) => {
      // Serialize resend attempts for the same email so concurrent requests cannot bypass the limit.
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [email]);

      const countRows = await manager.query(
        `SELECT COUNT(*)::int AS count
         FROM email.verification_resend_requests
         WHERE email = $1
           AND requested_at > NOW() - INTERVAL '${RESEND_WINDOW_MINUTES} minutes'`,
        [email],
      );
      const recentRequests = Number(countRows[0]?.count ?? 0);

      if (recentRequests >= RESEND_LIMIT) {
        throw new HttpException(
          {
            status: HttpStatus.TOO_MANY_REQUESTS,
            title: 'Too Many Requests',
            detail: 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.',
          },
          HttpStatus.TOO_MANY_REQUESTS,
        );
      }

      await manager.query(
        `INSERT INTO email.verification_resend_requests (email, requested_at)
         VALUES ($1, NOW())`,
        [email],
      );

      const rows = (await manager.query(
        `SELECT id, name, password_hash, role
         FROM email.pending_registrations
         WHERE email = $1 AND verified_at IS NULL
         LIMIT 1`,
        [email],
      )) as PendingRegistrationRow[];

      const registration = rows[0];
      if (!registration) return null;

      const token = newVerificationToken();
      await manager.query(
        `UPDATE email.pending_registrations
         SET token_hash = $2,
             token_expires_at = NOW() + INTERVAL '${VERIFICATION_TOKEN_TTL_HOURS} hours',
             updated_at = NOW()
         WHERE id = $1`,
        [registration.id, hashToken(token)],
      );

      return {
        name: registration.name,
        role: registration.role,
        token,
      };
    });

    if (pending) {
      try {
        await this.verificationMailer.sendVerification({
          to: email,
          name: pending.name,
          token: pending.token,
          role: pending.role,
        });
      } catch {
        // Deliberately omit the error object: it may contain recipient data or the verification URL.
        this.logger.error('Verification email was not accepted by the email provider.');
      }
    }

    return {
      message: 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.',
    };
  }

  async status(rawEmail: string, password: string): Promise<{ pending: boolean }> {
    const email = this.normalizeEmail(rawEmail);
    const rows = (await this.dataSource.query(
      `SELECT password_hash
       FROM email.pending_registrations
       WHERE email = $1 AND verified_at IS NULL
       LIMIT 1`,
      [email],
    )) as Array<{ password_hash: string }>;

    const passwordHash = rows[0]?.password_hash ?? (await this.dummyPasswordHash);
    const passwordMatches = await bcrypt.compare(password, passwordHash);

    return { pending: Boolean(rows[0]) && passwordMatches };
  }

  private normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }
}
