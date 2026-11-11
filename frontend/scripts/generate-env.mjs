import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

// Debe coincidir con el <script src="..."> de src/index.html; se valida más abajo
const ENV_FILE = 'env.js';

mkdirSync('public', { recursive: true });
const apiUrl = process.env.API_URL || '/api';
// Clave publicable de Stripe (pk_...): es pública por diseño, nunca usar aquí la sk_...
const stripeKey = process.env.STRIPE_PUBLISHABLE_KEY || '';
writeFileSync(`public/${ENV_FILE}`, `window.__env = ${JSON.stringify({ API_URL: apiUrl, STRIPE_PUBLISHABLE_KEY: stripeKey })};\n`);
console.log(`Generated public/${ENV_FILE} with API_URL=${apiUrl} STRIPE_PUBLISHABLE_KEY=${stripeKey ? 'set' : 'missing'}`);

// Si index.html carga otro archivo, la configuración generada nunca llega al navegador
// (así se perdió la clave de Stripe tras el merge de BUG-02). Se detiene el build en vez de fallar en silencio.
const indexHtml = readFileSync('src/index.html', 'utf8');
const loaded = [...indexHtml.matchAll(/<script[^>]*\ssrc=["']\.?\/?([^"']+)["']/g)].map(m => m[1]);
if (!loaded.includes(ENV_FILE)) {
  console.error(`ERROR: src/index.html no carga "${ENV_FILE}" (scripts encontrados: ${loaded.join(', ') || 'ninguno'}).`);
  console.error(`Haz que generate-env.mjs y el <script src> de index.html usen el mismo archivo.`);
  process.exit(1);
}
