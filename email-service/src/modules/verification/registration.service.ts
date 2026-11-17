import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { randomUUID } from 'node:crypto';
import * as jwt from 'jsonwebtoken';
import { Response } from 'express';
import { DataSource, Repository } from 'typeorm';
import { ProblemException } from '../../common/problem.exception';
import { RegisterDto } from './dto/register.dto';
import { VerifyDto } from './dto/verify.dto';
import { AppUser } from './entities/app-user.entity';
import { PendingRegistration } from './entities/pending-registration.entity';
import { hashToken, newVerificationToken } from './token.util';
import { VerificationMailerPort } from './verification-mailer.port';

@Injectable()
export class RegistrationService {
  private readonly logger = new Logger(RegistrationService.name);

  constructor(
    @InjectRepository(PendingRegistration)
    private readonly pendingRepo: Repository<PendingRegistration>,
    @InjectRepository(AppUser)
    private readonly userRepo: Repository<AppUser>,
    private readonly mailer: VerificationMailerPort,
    private readonly configService: ConfigService,
    private readonly dataSource: DataSource
  ) {}

  async register(
    dto: RegisterDto,
    role: 'CUSTOMER' | 'PROVIDER'
  ): Promise<{ email: string; message: string }> {
    const normalizedEmail = dto.email.trim().toLowerCase();
    const trimmedName = dto.name.trim();

    if (trimmedName.length > 120) {
      throw new ProblemException(400, 'Revisa los datos del formulario.');
    }

    const existingUser = await this.userRepo.findOne({
      where: { email: normalizedEmail }
    });
    if (existingUser) {
      throw new ProblemException(422, 'El correo ya está registrado');
    }

    const passwordHash = await bcrypt.hash(dto.password, 10);
    const { token, hash, expiresAt } = newVerificationToken();

    let pending = await this.pendingRepo.findOne({
      where: { email: normalizedEmail }
    });

    if (pending) {
      pending.name = trimmedName;
      pending.passwordHash = passwordHash;
      pending.role = role;
      pending.tokenHash = hash;
      pending.tokenExpiresAt = expiresAt;
      pending.verifiedAt = null;
      pending.updatedAt = new Date();
    } else {
      pending = this.pendingRepo.create({
        id: randomUUID(),
        email: normalizedEmail,
        name: trimmedName,
        passwordHash,
        role,
        tokenHash: hash,
        tokenExpiresAt: expiresAt,
        verifiedAt: null
      });
    }

    await this.pendingRepo.save(pending);

    try {
      await this.mailer.sendVerification({
        to: normalizedEmail,
        name: trimmedName,
        token,
        role
      });
    } catch {
      this.logger.error('Error al enviar correo de verificación');
    }

    return {
      email: normalizedEmail,
      message: 'Te enviamos un enlace de verificación. Revisa tu correo para activar tu cuenta.'
    };
  }

  async verify(
    dto: VerifyDto,
    res: Response
  ): Promise<{ email: string; name: string; role: string } | { status: string; message: string }> {
    const hash = hashToken(dto.token);

    const pending = await this.pendingRepo.findOne({
      where: { tokenHash: hash }
    });

    if (!pending) {
      throw new ProblemException(
        400,
        'El enlace de verificación es inválido.',
        'TOKEN_INVALID'
      );
    }

    if (pending.verifiedAt !== null) {
      throw new ProblemException(
        400,
        'El enlace de verificación ya fue utilizado.',
        'TOKEN_USED'
      );
    }

    if (new Date(pending.tokenExpiresAt).getTime() < Date.now()) {
      throw new ProblemException(
        400,
        'El enlace de verificación venció.',
        'TOKEN_EXPIRED'
      );
    }

    await this.dataSource.transaction(async (manager) => {
      const existingUser = await manager.findOne(AppUser, {
        where: { email: pending.email }
      });

      if (existingUser) {
        pending.verifiedAt = new Date();
        await manager.save(pending);
        throw new ProblemException(
          400,
          'El enlace de verificación ya fue utilizado.',
          'TOKEN_USED'
        );
      }

      const user = manager.create(AppUser, {
        id: randomUUID(),
        name: pending.name,
        email: pending.email,
        password: pending.passwordHash,
        role: pending.role,
        status: pending.role === 'CUSTOMER' ? 'ACTIVE' : 'PENDING_APPROVAL',
        createdAt: new Date()
      });

      try {
        await manager.save(user);
      } catch {
        pending.verifiedAt = new Date();
        await manager.save(pending);
        throw new ProblemException(
          400,
          'El enlace de verificación ya fue utilizado.',
          'TOKEN_USED'
        );
      }

      pending.verifiedAt = new Date();
      await manager.save(pending);
    });

    if (pending.role === 'CUSTOMER') {
      const jwtSecret = this.configService.getOrThrow<string>('JWT_SECRET');
      const key = Buffer.from(jwtSecret, 'base64');
      const ttlMs = Number(this.configService.get('JWT_EXPIRATION') ?? 28800000);

      const jwtToken = jwt.sign(
        { role: pending.role },
        key,
        {
          algorithm: 'HS256',
          subject: pending.email,
          expiresIn: Math.floor(ttlMs / 1000)
        }
      );

      res.cookie('sb_token', jwtToken, {
        httpOnly: true,
        secure: this.configService.get('AUTH_COOKIE_SECURE') !== 'false',
        sameSite: 'strict',
        path: '/api',
        maxAge: ttlMs
      });

      return {
        email: pending.email,
        name: pending.name,
        role: pending.role
      };
    }

    res.status(202);
    return {
      status: 'PENDING_APPROVAL',
      message: 'Correo verificado. Un administrador revisará tu perfil y podrás iniciar sesión cuando lo apruebe.'
    };
  }
}
