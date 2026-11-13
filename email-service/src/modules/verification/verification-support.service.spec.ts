import * as bcrypt from 'bcryptjs';
import { ProblemException } from '../../common/problem.exception';
import { VerificationSupportService } from './verification-support.service';
import { hashToken } from './token.util';

describe('VerificationSupportService', () => {
  let pendingRepo: any;
  let resendRepo: any;
  let mailer: any;
  let service: VerificationSupportService;

  const pendingAna = (overrides = {}) => ({
    email: 'ana@test.com',
    name: 'Ana',
    role: 'CUSTOMER',
    passwordHash: bcrypt.hashSync('clave-secreta', 4),
    tokenHash: 'hash-viejo',
    tokenExpiresAt: new Date(),
    verifiedAt: null,
    ...overrides,
  });

  beforeEach(() => {
    pendingRepo = {
      findOne: jest.fn().mockResolvedValue(null),
      save: jest.fn((entity) => Promise.resolve(entity)),
    };
    resendRepo = {
      count: jest.fn().mockResolvedValue(0),
      insert: jest.fn().mockResolvedValue(undefined),
    };
    mailer = { sendVerification: jest.fn().mockResolvedValue(undefined) };
    service = new VerificationSupportService(pendingRepo, resendRepo, mailer);
  });

  describe('resend', () => {
    it('responde el mensaje genérico aunque el correo no exista, y no envía nada', async () => {
      const result = await service.resend('  NADIE@Test.com ');

      expect(result.message).toBe('Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.');
      expect(resendRepo.insert).toHaveBeenCalledWith({ email: 'nadie@test.com' });
      expect(mailer.sendVerification).not.toHaveBeenCalled();
    });

    it('con un pendiente, genera un token nuevo (invalida el anterior) y envía el correo con él', async () => {
      const pending = pendingAna();
      pendingRepo.findOne.mockResolvedValue(pending);

      await service.resend('ana@test.com');

      const saved = pendingRepo.save.mock.calls[0][0];
      expect(saved.tokenHash).not.toBe('hash-viejo');
      expect(saved.tokenExpiresAt.getTime()).toBeGreaterThan(Date.now() + 23 * 60 * 60 * 1000);

      const mail = mailer.sendVerification.mock.calls[0][0];
      expect(mail).toMatchObject({ to: 'ana@test.com', name: 'Ana', role: 'CUSTOMER' });
      expect(hashToken(mail.token)).toBe(saved.tokenHash);
    });

    it('si el registro ya se verificó, no genera token ni envía nada', async () => {
      pendingRepo.findOne.mockResolvedValue(pendingAna({ verifiedAt: new Date() }));

      await service.resend('ana@test.com');

      expect(pendingRepo.save).not.toHaveBeenCalled();
      expect(mailer.sendVerification).not.toHaveBeenCalled();
    });

    it('a la 4.ª petición en 15 minutos responde 429, aunque el correo no exista', async () => {
      resendRepo.count.mockResolvedValue(3);

      const error = await service.resend('nadie@test.com').catch((e) => e);

      expect(error).toBeInstanceOf(ProblemException);
      expect(error.getStatus()).toBe(429);
      expect(resendRepo.insert).not.toHaveBeenCalled();
      expect(pendingRepo.findOne).not.toHaveBeenCalled();
    });

    it('si falla el envío, responde igual', async () => {
      pendingRepo.findOne.mockResolvedValue(pendingAna());
      mailer.sendVerification.mockRejectedValue(new Error('proveedor caído'));

      await expect(service.resend('ana@test.com')).resolves.toHaveProperty('message');
    });
  });

  describe('status', () => {
    it('pendiente y contraseña correcta → pending: true', async () => {
      pendingRepo.findOne.mockResolvedValue(pendingAna());
      await expect(service.status('ANA@test.com ', 'clave-secreta')).resolves.toEqual({ pending: true });
    });

    it('pendiente y contraseña incorrecta → pending: false', async () => {
      pendingRepo.findOne.mockResolvedValue(pendingAna());
      await expect(service.status('ana@test.com', 'otra-clave')).resolves.toEqual({ pending: false });
    });

    it('ya verificado → pending: false', async () => {
      pendingRepo.findOne.mockResolvedValue(pendingAna({ verifiedAt: new Date() }));
      await expect(service.status('ana@test.com', 'clave-secreta')).resolves.toEqual({ pending: false });
    });

    it('correo inexistente → pending: false, pero igualmente compara contra un hash de relleno', async () => {
      const compare = jest.spyOn(bcrypt, 'compare');
      await expect(service.status('nadie@test.com', 'clave-secreta')).resolves.toEqual({ pending: false });
      expect(compare).toHaveBeenCalledTimes(1);
      compare.mockRestore();
    });
  });
});
