import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../../email/email-sender.port';
import { VerificationMailer } from '../verification-mailer';

describe('VerificationMailer', () => {
  it('builds the frontend URL and sends the rendered email', async () => {
    const config = {
      get: jest.fn().mockReturnValue('https://skillbridge.example/'),
    } as unknown as ConfigService;
    const emailSender = { send: jest.fn().mockResolvedValue(undefined) } as unknown as EmailSenderPort;
    const mailer = new VerificationMailer(config, emailSender);

    await mailer.sendVerification({
      to: 'ada@example.com',
      name: 'Ada',
      token: 'token with spaces',
      role: 'CUSTOMER',
    });

    expect(emailSender.send).toHaveBeenCalledWith(
      expect.objectContaining({
        to: 'ada@example.com',
        recipientName: 'Ada',
        subject: 'Verifica tu correo en SkillBridge',
        html: expect.stringContaining(
          'https://skillbridge.example/verificar-correo?token=token%20with%20spaces',
        ),
        text: expect.stringContaining(
          'https://skillbridge.example/verificar-correo?token=token%20with%20spaces',
        ),
      }),
    );
  });
});
