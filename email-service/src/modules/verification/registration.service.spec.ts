import { ConfigService } from '@nestjs/config';
import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import * as jwt from 'jsonwebtoken';
import { Response } from 'express';
import { DataSource } from 'typeorm';
import { ProblemException } from '../../common/problem.exception';
import { AppUser } from './entities/app-user.entity';
import { PendingRegistration } from './entities/pending-registration.entity';
import { RegistrationService } from './registration.service';
import { hashToken } from './token.util';
import { VerificationMailerPort } from './verification-mailer.port';

describe('RegistrationService', () => {
  let service: RegistrationService;
  let pendingRepo: any;
  let userRepo: any;
  let mailer: any;
  let configService: any;
  let dataSource: any;
  let mockEntityManager: any;

  const mockJwtSecret = 'NDBlNjM1MjY2NTVhNThnZTJyMzU3NTM4Nzh4MmY0MTNmMjQyOGc3MmI0YjYyNTA2NDUzNjc1NjZiNTk3cA==';

  beforeEach(async () => {
    pendingRepo = {
      findOne: jest.fn(),
      create: jest.fn((entity) => entity),
      save: jest.fn((entity) => Promise.resolve(entity))
    };

    userRepo = {
      findOne: jest.fn()
    };

    mailer = {
      sendVerification: jest.fn().mockResolvedValue(undefined)
    };

    configService = {
      get: jest.fn((key: string) => {
        if (key === 'JWT_EXPIRATION') return 28800000;
        if (key === 'AUTH_COOKIE_SECURE') return 'false';
        return undefined;
      }),
      getOrThrow: jest.fn((key: string) => {
        if (key === 'JWT_SECRET') return mockJwtSecret;
        throw new Error(`Missing config: ${key}`);
      })
    };

    mockEntityManager = {
      findOne: jest.fn(),
      create: jest.fn((_cls, entity) => entity),
      save: jest.fn((entity) => Promise.resolve(entity))
    };

    dataSource = {
      transaction: jest.fn((cb) => cb(mockEntityManager))
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RegistrationService,
        { provide: getRepositoryToken(PendingRegistration), useValue: pendingRepo },
        { provide: getRepositoryToken(AppUser), useValue: userRepo },
        { provide: VerificationMailerPort, useValue: mailer },
        { provide: ConfigService, useValue: configService },
        { provide: DataSource, useValue: dataSource }
      ]
    }).compile();

    service = module.get<RegistrationService>(RegistrationService);
  });

  describe('register', () => {
    it('should register a new customer successfully', async () => {
      userRepo.findOne.mockResolvedValue(null);
      pendingRepo.findOne.mockResolvedValue(null);

      const result = await service.register(
        { name: '  Ada Lovelace  ', email: ' ADA@Example.com ', password: 'Password123' },
        'CUSTOMER'
      );

      expect(result).toEqual({
        email: 'ada@example.com',
        message: 'Te enviamos un enlace de verificación. Revisa tu correo para activar tu cuenta.'
      });

      expect(userRepo.findOne).toHaveBeenCalledWith({ where: { email: 'ada@example.com' } });
      expect(pendingRepo.save).toHaveBeenCalledWith(
        expect.objectContaining({
          email: 'ada@example.com',
          name: 'Ada Lovelace',
          role: 'CUSTOMER',
          verifiedAt: null
        })
      );
      expect(mailer.sendVerification).toHaveBeenCalledWith(
        expect.objectContaining({
          to: 'ada@example.com',
          name: 'Ada Lovelace',
          role: 'CUSTOMER'
        })
      );
    });

    it('should replace an existing pending registration (upsert)', async () => {
      userRepo.findOne.mockResolvedValue(null);
      const existingPending = {
        id: 'existing-id',
        email: 'ada@example.com',
        name: 'Old Name',
        passwordHash: 'old_hash',
        role: 'CUSTOMER',
        tokenHash: 'old_token_hash',
        tokenExpiresAt: new Date(),
        verifiedAt: new Date(Date.now() - 1000)
      };
      pendingRepo.findOne.mockResolvedValue(existingPending);

      const result = await service.register(
        { name: 'Ada New', email: 'ada@example.com', password: 'NewPassword123' },
        'PROVIDER'
      );

      expect(result.email).toBe('ada@example.com');
      expect(existingPending.name).toBe('Ada New');
      expect(existingPending.role).toBe('PROVIDER');
      expect(existingPending.verifiedAt).toBeNull();
      expect(pendingRepo.save).toHaveBeenCalledWith(existingPending);
    });

    it('should throw 422 if email already exists in app_users', async () => {
      userRepo.findOne.mockResolvedValue({ id: 'some-user-id', email: 'ada@example.com' });

      await expect(
        service.register(
          { name: 'Ada', email: 'ada@example.com', password: 'Password123' },
          'CUSTOMER'
        )
      ).rejects.toThrow(ProblemException);

      try {
        await service.register(
          { name: 'Ada', email: 'ada@example.com', password: 'Password123' },
          'CUSTOMER'
        );
      } catch (err: any) {
        expect(err.getStatus()).toBe(422);
        expect(err.getResponse().detail).toBe('El correo ya está registrado');
      }
    });

    it('should still return 202 if mailer fails', async () => {
      userRepo.findOne.mockResolvedValue(null);
      pendingRepo.findOne.mockResolvedValue(null);
      mailer.sendVerification.mockRejectedValue(new Error('SMTP down'));

      const result = await service.register(
        { name: 'Ada', email: 'ada@example.com', password: 'Password123' },
        'CUSTOMER'
      );

      expect(result.email).toBe('ada@example.com');
      expect(result.message).toBeDefined();
    });
  });

  describe('verify', () => {
    const rawToken = 'test-token-123';
    const validHash = hashToken(rawToken);

    it('should throw 400 TOKEN_INVALID if token does not exist', async () => {
      pendingRepo.findOne.mockResolvedValue(null);
      const res = {} as Response;

      await expect(service.verify({ token: rawToken }, res)).rejects.toMatchObject({
        status: 400,
        reason: 'TOKEN_INVALID'
      });
    });

    it('should throw 400 TOKEN_USED if verified_at is not null', async () => {
      pendingRepo.findOne.mockResolvedValue({
        tokenHash: validHash,
        verifiedAt: new Date(),
        tokenExpiresAt: new Date(Date.now() + 100000)
      });
      const res = {} as Response;

      await expect(service.verify({ token: rawToken }, res)).rejects.toMatchObject({
        status: 400,
        reason: 'TOKEN_USED'
      });
    });

    it('should throw 400 TOKEN_EXPIRED if token is expired', async () => {
      pendingRepo.findOne.mockResolvedValue({
        tokenHash: validHash,
        verifiedAt: null,
        tokenExpiresAt: new Date(Date.now() - 1000)
      });
      const res = {} as Response;

      await expect(service.verify({ token: rawToken }, res)).rejects.toMatchObject({
        status: 400,
        reason: 'TOKEN_EXPIRED'
      });
    });

    it('should verify customer: insert into app_users, set cookie sb_token, and return 200', async () => {
      const pending = {
        id: 'pending-id',
        email: 'customer@example.com',
        name: 'Ada Customer',
        passwordHash: await bcrypt.hash('Password123', 10),
        role: 'CUSTOMER' as const,
        tokenHash: validHash,
        tokenExpiresAt: new Date(Date.now() + 86400000),
        verifiedAt: null
      };
      pendingRepo.findOne.mockResolvedValue(pending);
      mockEntityManager.findOne.mockResolvedValue(null);

      const mockCookie = jest.fn();
      const res = { cookie: mockCookie, status: jest.fn() } as unknown as Response;

      const result = await service.verify({ token: rawToken }, res);

      expect(mockEntityManager.create).toHaveBeenCalledWith(
        AppUser,
        expect.objectContaining({
          email: 'customer@example.com',
          name: 'Ada Customer',
          role: 'CUSTOMER',
          status: 'ACTIVE'
        })
      );
      expect(mockEntityManager.save).toHaveBeenCalled();
      expect(pending.verifiedAt).not.toBeNull();
      expect(mockCookie).toHaveBeenCalledWith(
        'sb_token',
        expect.any(String),
        expect.objectContaining({
          httpOnly: true,
          sameSite: 'strict',
          path: '/api',
          maxAge: 28800000
        })
      );

      // Verify the cookie token
      const cookieCall = mockCookie.mock.calls[0];
      const token = cookieCall[1];
      const decoded: any = jwt.verify(token, Buffer.from(mockJwtSecret, 'base64'), { algorithms: ['HS256'] });
      expect(decoded.sub).toBe('customer@example.com');
      expect(decoded.role).toBe('CUSTOMER');

      expect(result).toEqual({
        email: 'customer@example.com',
        name: 'Ada Customer',
        role: 'CUSTOMER'
      });
    });

    it('should verify provider: insert into app_users with PENDING_APPROVAL, no cookie, and return 202', async () => {
      const pending = {
        id: 'pending-id',
        email: 'provider@example.com',
        name: 'Grace Provider',
        passwordHash: await bcrypt.hash('Password123', 10),
        role: 'PROVIDER' as const,
        tokenHash: validHash,
        tokenExpiresAt: new Date(Date.now() + 86400000),
        verifiedAt: null
      };
      pendingRepo.findOne.mockResolvedValue(pending);
      mockEntityManager.findOne.mockResolvedValue(null);

      const mockCookie = jest.fn();
      const mockStatus = jest.fn();
      const res = { cookie: mockCookie, status: mockStatus } as unknown as Response;

      const result = await service.verify({ token: rawToken }, res);

      expect(mockEntityManager.create).toHaveBeenCalledWith(
        AppUser,
        expect.objectContaining({
          email: 'provider@example.com',
          role: 'PROVIDER',
          status: 'PENDING_APPROVAL'
        })
      );
      expect(mockCookie).not.toHaveBeenCalled();
      expect(mockStatus).toHaveBeenCalledWith(202);
      expect(result).toEqual({
        status: 'PENDING_APPROVAL',
        message: 'Correo verificado. Un administrador revisará tu perfil y podrás iniciar sesión cuando lo apruebe.'
      });
    });

    it('should throw TOKEN_USED if app_users already has the email during transaction', async () => {
      const pending = {
        id: 'pending-id',
        email: 'collision@example.com',
        name: 'Collision',
        passwordHash: 'hash',
        role: 'CUSTOMER' as const,
        tokenHash: validHash,
        tokenExpiresAt: new Date(Date.now() + 86400000),
        verifiedAt: null
      };
      pendingRepo.findOne.mockResolvedValue(pending);
      mockEntityManager.findOne.mockResolvedValue({ id: 'already-existing' });

      const res = {} as Response;

      await expect(service.verify({ token: rawToken }, res)).rejects.toMatchObject({
        status: 400,
        reason: 'TOKEN_USED'
      });
      expect(pending.verifiedAt).not.toBeNull();
      expect(mockEntityManager.save).toHaveBeenCalledWith(pending);
    });
  });
});
