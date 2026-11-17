import { renderVerificationEmail } from './verification-email.template';

describe('renderVerificationEmail', () => {
  const verifyUrl = 'http://localhost:4200/verificar-correo?token=abc%2B123';
  const base = { name: 'Ana', verifyUrl, role: 'CUSTOMER' as const };

  it('usa el asunto acordado', () => {
    expect(renderVerificationEmail(base).subject).toBe('Verifica tu correo en SkillBridge');
  });

  it('el HTML trae el botón "Verificar mi correo" que lleva a verifyUrl', () => {
    const { html } = renderVerificationEmail(base);
    expect(html).toContain(`href="${verifyUrl}"`);
    expect(html).toContain('Verificar mi correo');
    expect(html).toContain('vence en 24 horas y solo sirve una vez');
    expect(html).toContain('Si no creaste esta cuenta, ignora este correo');
  });

  it('el texto plano trae la URL completa', () => {
    const { text } = renderVerificationEmail(base);
    expect(text).toContain(verifyUrl);
    expect(text).toContain('vence en 24 horas y solo sirve una vez');
  });

  it('escapa el nombre para que no se pueda meter HTML', () => {
    const { html } = renderVerificationEmail({ ...base, name: '<script>alert(1)</script>' });
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
  });

  it('solo al proveedor le avisa de la revisión del administrador', () => {
    const frase = 'un administrador revisará tu perfil';
    expect(renderVerificationEmail(base).html).not.toContain(frase);
    const proveedor = renderVerificationEmail({ ...base, role: 'PROVIDER' });
    expect(proveedor.html).toContain(frase);
    expect(proveedor.text).toContain(frase);
  });
});
