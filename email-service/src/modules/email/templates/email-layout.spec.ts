import { renderHtmlLayout, renderTextLayout } from './email-layout';

describe('email-layout', () => {
  it('escapa título, botón y pie, y deja el cuerpo tal cual', () => {
    const html = renderHtmlLayout({
      heading: 'Hola <b>',
      bodyHtml: '<p>cuerpo</p>',
      button: { label: 'Ir', url: 'https://x.dev/?a=1&b=2' },
      footer: 'Pie & fin',
    });
    expect(html).toContain('Hola &lt;b&gt;');
    expect(html).toContain('<p>cuerpo</p>');
    expect(html).toContain('href="https://x.dev/?a=1&amp;b=2"');
    expect(html).toContain('Pie &amp; fin');
  });

  it('sin botón no pinta enlace', () => {
    expect(renderHtmlLayout({ heading: 'H', bodyHtml: '', footer: 'F' })).not.toContain('<a ');
  });

  it('el texto plano pone el botón como "etiqueta: url" y el pie al final', () => {
    const text = renderTextLayout({ lines: ['Hola'], button: { label: 'Ir', url: 'https://x.dev' }, footer: 'Pie' });
    expect(text).toBe('Hola\n\nIr: https://x.dev\n\nPie');
  });
});
