import { createHash } from 'node:crypto';
import { hashToken, newVerificationToken, VERIFICATION_TTL_MS } from './token.util';

describe('token.util', () => {
  it('should generate a 64-char sha256 hex hash from token', () => {
    const token = 'sample-token';
    const expected = createHash('sha256').update(token).digest('hex');
    expect(hashToken(token)).toBe(expected);
    expect(hashToken(token)).toHaveLength(64);
  });

  it('should generate newVerificationToken with token, hash, and expiresAt in 24 hours', () => {
    const before = Date.now();
    const result = newVerificationToken();
    const after = Date.now();

    expect(result.token).toBeDefined();
    expect(typeof result.token).toBe('string');
    expect(result.hash).toBe(hashToken(result.token));
    expect(result.expiresAt.getTime()).toBeGreaterThanOrEqual(before + VERIFICATION_TTL_MS);
    expect(result.expiresAt.getTime()).toBeLessThanOrEqual(after + VERIFICATION_TTL_MS);
  });
});
