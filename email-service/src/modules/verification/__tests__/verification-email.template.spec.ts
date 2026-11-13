import { buildVerificationEmailTemplate } from '../templates/verification-email.template';

describe('buildVerificationEmailTemplate', () => {
  it('includes the verification button and URL', () => {
    const result = buildVerificationEmailTemplate({
      name: 'Ada',
      verifyUrl: 'https://skillbridge.example/verificar-correo?token=abc123',
      role: 'CUSTOMER',
    });

    expect(result.subject).toBe('Verifica tu correo en SkillBridge');
    expect(result.html).toContain('Verificar mi correo');
    expect(result.html).toContain('href="https://skillbridge.example/verificar-correo?token=abc123"');
    expect(result.html).toContain('El enlace vence en 24 horas y solo sirve una vez');
    expect(result.text).toContain('https://skillbridge.example/verificar-correo?token=abc123');
  });

  it('escapes a name that contains HTML', () => {
    const result = buildVerificationEmailTemplate({
      name: '<script>alert(1)</script>',
      verifyUrl: 'https://skillbridge.example/verify?token=x',
      role: 'CUSTOMER',
    });

    expect(result.html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(result.html).not.toContain('<script>alert(1)</script>');
  });

  it('adds the administrator review notice for providers', () => {
    const result = buildVerificationEmailTemplate({
      name: 'Provider',
      verifyUrl: 'https://skillbridge.example/verify?token=x',
      role: 'PROVIDER',
    });

    expect(result.html).toContain(
      'Después de verificarlo, un administrador revisará tu perfil antes de que puedas iniciar sesión',
    );
    expect(result.text).toContain('un administrador revisará tu perfil');
  });
});
