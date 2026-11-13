import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { MoreThan, Repository } from 'typeorm';
import { ProblemException } from '../../common/problem.exception';
import { PendingRegistration } from './entities/pending-registration.entity';
import { VerificationResendRequest } from './entities/verification-resend-request.entity';
import { newVerificationToken } from './token.util';
import { VerificationMailerPort } from './verification-mailer.port';

const RESEND_LIMIT = 3;
const RESEND_WINDOW_MS = 15 * 60 * 1000;
const RESEND_MESSAGE = 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.';
// Si el correo no existe se compara contra este hash, para que /status tarde lo mismo y no revele qué correos hay
const DUMMY_HASH = bcrypt.hashSync('relleno-para-igualar-tiempos', 10);

const normalize = (email: string) => email.trim().toLowerCase();

@Injectable()
export class VerificationSupportService {
  private readonly logger = new Logger(VerificationSupportService.name);

  constructor(
    @InjectRepository(PendingRegistration)
    private readonly pendingRepo: Repository<PendingRegistration>,
    @InjectRepository(VerificationResendRequest)
    private readonly resendRepo: Repository<VerificationResendRequest>,
    private readonly mailer: VerificationMailerPort,
  ) {}

  // Responde siempre lo mismo exista o no el correo; el límite cuenta también los correos inexistentes,
  // porque si no, el 429 dejaría adivinar cuáles están pendientes.
  async resend(rawEmail: string): Promise<{ message: string }> {
    const email = normalize(rawEmail);

    const recent = await this.resendRepo.count({
      where: { email, requestedAt: MoreThan(new Date(Date.now() - RESEND_WINDOW_MS)) },
    });
    if (recent >= RESEND_LIMIT) {
      throw new ProblemException(429, 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.');
    }
    await this.resendRepo.insert({ email });

    const pending = await this.pendingRepo.findOne({ where: { email } });
    if (pending && pending.verifiedAt === null) {
      const { token, hash, expiresAt } = newVerificationToken();
      pending.tokenHash = hash; // el enlace anterior deja de valer
      pending.tokenExpiresAt = expiresAt;
      pending.updatedAt = new Date();
      await this.pendingRepo.save(pending);

      try {
        await this.mailer.sendVerification({ to: email, name: pending.name, token, role: pending.role });
      } catch {
        this.logger.error('Error al reenviar el correo de verificación');
      }
    }

    return { message: RESEND_MESSAGE };
  }

  async status(rawEmail: string, password: string): Promise<{ pending: boolean }> {
    const pending = await this.pendingRepo.findOne({ where: { email: normalize(rawEmail) } });
    const passwordOk = await bcrypt.compare(password, pending?.passwordHash ?? DUMMY_HASH);
    return { pending: !!pending && pending.verifiedAt === null && passwordOk };
  }
}
