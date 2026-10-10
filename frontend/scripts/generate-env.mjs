import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });
const apiUrl = process.env.API_URL || '/api';
// Clave publicable de Stripe (pk_...): es pública por diseño, nunca usar aquí la sk_...
const stripeKey = process.env.STRIPE_PUBLISHABLE_KEY || '';
writeFileSync('public/env.js', `window.__env = ${JSON.stringify({ API_URL: apiUrl, STRIPE_PUBLISHABLE_KEY: stripeKey })};\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl} STRIPE_PUBLISHABLE_KEY=${stripeKey ? 'set' : 'missing'}`);
