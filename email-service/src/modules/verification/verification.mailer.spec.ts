import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { VerificationMailer } from './verification.mailer';

describe('VerificationMailer', () => {
  const mail = { to: 'ana@test.com', name: 'Ana', token: 'a+b/c=', role: 'CUSTOMER' as const };

  function build(frontendUrl: string) {
    const sender = { send: jest.fn().mockResolvedValue(undefined) };
    const config = { getOrThrow: jest.fn().mockReturnValue(frontendUrl) } as unknown as ConfigService;
    return { sender, mailer: new VerificationMailer(sender as EmailSenderPort, config) };
  }

  it('envía el correo con el enlace al frontend y el token codificado', async () => {
    const { sender, mailer } = build('http://localhost:4200');
    await mailer.sendVerification(mail);

    expect(sender.send).toHaveBeenCalledTimes(1);
    const message = sender.send.mock.calls[0][0];
    expect(message.to).toBe('ana@test.com');
    expect(message.recipientName).toBe('Ana');
    expect(message.subject).toBe('Verifica tu correo en SkillBridge');
    expect(message.text).toContain('http://localhost:4200/verificar-correo?token=a%2Bb%2Fc%3D');
  });

  it('no duplica la barra si FRONTEND_URL termina en /', async () => {
    const { sender, mailer } = build('https://skillbridge.vercel.app/');
    await mailer.sendVerification(mail);
    expect(sender.send.mock.calls[0][0].text).toContain('https://skillbridge.vercel.app/verificar-correo?token=');
  });

  it('propaga el error si el proveedor no acepta el correo', async () => {
    const { sender, mailer } = build('http://localhost:4200');
    sender.send.mockRejectedValue(new Error('caído'));
    await expect(mailer.sendVerification(mail)).rejects.toThrow('caído');
  });
});
