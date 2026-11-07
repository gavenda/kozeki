import { createMiddleware } from 'hono/factory';
import { verifyAccessToken } from '../auth0';
import { unauthorized } from '../errors';
import { ensureUser } from '../users';
import type { AppEnv } from '../types';

/** Extracts the token from an `Authorization: Bearer <token>` header. */
export function bearerToken(header: string | undefined): string | null {
	if (!header) return null;
	const match = /^Bearer\s+(.+)$/i.exec(header.trim());
	return match ? match[1].trim() : null;
}

/**
 * Rejects the request unless it carries a valid Auth0 access token, then pins the owner for
 * everything downstream. `userId` is the Auth0 subject and the only thing routes scope data
 * by: it comes from a verified signature, never from the request.
 */
export const requireAuth = createMiddleware<AppEnv>(async (c, next) => {
	const token = bearerToken(c.req.header('Authorization'));
	if (!token) throw unauthorized();

	const claims = await verifyAccessToken(c.env, token);
	c.set('claims', claims);
	c.set('userId', claims.sub);

	await ensureUser(c.env, claims.sub);
	await next();
});
