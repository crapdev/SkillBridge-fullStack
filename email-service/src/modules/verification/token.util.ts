import { createHash, randomBytes } from 'node:crypto';

export const VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000;

export const hashToken = (token: string): string =>
  createHash('sha256').update(token).digest('hex');

export function newVerificationToken(): { token: string; hash: string; expiresAt: Date } {
  const token = randomBytes(32).toString('base64url'); // va en el enlace del correo
  return { token, hash: hashToken(token), expiresAt: new Date(Date.now() + VERIFICATION_TTL_MS) };
}
