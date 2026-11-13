import * as bcrypt from 'bcryptjs';
import { DataSource } from 'typeorm';
import { VerificationMailerPort } from '../verification-mailer.port';
import { VerificationSupportService } from '../verification-support.service';

jest.mock('bcryptjs', () => ({
  hash: jest.fn().mockResolvedValue('dummy-hash'),
  compare: jest.fn(),
}));

describe('VerificationSupportService', () => {
  let service: VerificationSupportService;
  let manager: { query: jest.Mock };
  let dataSource: { transaction: jest.Mock; query: jest.Mock };
  let mailer: { sendVerification: jest.Mock };

  beforeEach(() => {
    jest.clearAllMocks();
    manager = { query: jest.fn() };
    dataSource = {
      transaction: jest.fn((callback) => callback(manager)),
      query: jest.fn(),
    };
    mailer = { sendVerification: jest.fn().mockResolvedValue(undefined) };
    service = new VerificationSupportService(
      dataSource as unknown as DataSource,
      mailer as unknown as VerificationMailerPort,
    );
  });

  it('returns the same 202 message when the email has no pending registration', async () => {
    manager.query
      .mockResolvedValueOnce([]) // advisory lock
      .mockResolvedValueOnce([{ count: 0 }])
      .mockResolvedValueOnce([]) // insert resend attempt
      .mockResolvedValueOnce([]); // no pending registration

    const result = await service.resend(' ADA@Example.com ');

    expect(result).toEqual({
      message: 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.',
    });
    expect(manager.query).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO email.verification_resend_requests'),
      ['ada@example.com'],
    );
    expect(mailer.sendVerification).not.toHaveBeenCalled();
  });

  it('returns the same 202 message and sends a fresh token when a pending registration exists', async () => {
    manager.query
      .mockResolvedValueOnce([]) // advisory lock
      .mockResolvedValueOnce([{ count: 1 }])
      .mockResolvedValueOnce([]) // insert resend attempt
      .mockResolvedValueOnce([{
        id: 'pending-id',
        name: 'Ada',
        password_hash: 'password-hash',
        role: 'CUSTOMER',
      }])
      .mockResolvedValueOnce([]); // update token

    await expect(service.resend('ada@example.com')).resolves.toEqual({
      message: 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.',
    });

    expect(manager.query).toHaveBeenCalledWith(
      expect.stringContaining('UPDATE email.pending_registrations'),
      expect.arrayContaining(['pending-id', expect.any(String)]),
    );
    expect(mailer.sendVerification).toHaveBeenCalledWith(
      expect.objectContaining({
        to: 'ada@example.com',
        name: 'Ada',
        role: 'CUSTOMER',
        token: expect.any(String),
      }),
    );
  });

  it('returns 429 on the fourth request in the 15-minute window', async () => {
    manager.query
      .mockResolvedValueOnce([]) // advisory lock
      .mockResolvedValueOnce([{ count: 3 }]);

    await expect(service.resend('unknown@example.com')).rejects.toMatchObject({
      status: 429,
      response: {
        status: 429,
        detail: 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.',
      },
    });
    expect(manager.query).toHaveBeenCalledTimes(2); // lock + count; no insert
  });

  it('still returns 202 when the email provider rejects the message', async () => {
    manager.query
      .mockResolvedValueOnce([]) // advisory lock
      .mockResolvedValueOnce([{ count: 0 }])
      .mockResolvedValueOnce([]) // insert resend attempt
      .mockResolvedValueOnce([{
        id: 'pending-id',
        name: 'Ada',
        password_hash: 'password-hash',
        role: 'CUSTOMER',
      }])
      .mockResolvedValueOnce([]); // update token
    mailer.sendVerification.mockRejectedValueOnce(new Error('provider failure'));

    await expect(service.resend('ada@example.com')).resolves.toEqual({
      message: 'Si ese correo tiene un registro pendiente, te enviamos un enlace nuevo.',
    });
  });

  it('returns pending true only when the password matches', async () => {
    dataSource.query.mockResolvedValueOnce([{ password_hash: 'stored-hash' }]);
    (bcrypt.compare as jest.Mock).mockResolvedValueOnce(true);

    await expect(service.status(' ADA@Example.com ', 'correct-password')).resolves.toEqual({
      pending: true,
    });
    expect(dataSource.query).toHaveBeenCalledWith(expect.stringContaining('email.pending_registrations'), [
      'ada@example.com',
    ]);
    expect(bcrypt.compare).toHaveBeenCalledWith('correct-password', 'stored-hash');
  });

  it('performs a dummy bcrypt comparison when the email is unknown', async () => {
    dataSource.query.mockResolvedValueOnce([]);
    (bcrypt.compare as jest.Mock).mockResolvedValueOnce(false);

    await expect(service.status('unknown@example.com', 'any-password')).resolves.toEqual({
      pending: false,
    });
    expect(bcrypt.compare).toHaveBeenCalledWith('any-password', 'dummy-hash');
  });
});
