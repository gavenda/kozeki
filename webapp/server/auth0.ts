import type { HonoJsonWebKey } from 'hono/utils/jwt/jws';
import type { JWTPayload } from 'hono/utils/jwt/types';
import { Jwt } from 'hono/utils/jwt';
import { HttpError, unauthorized } from './errors';

/**
 * Auth0 signs with RS256. Pinning the list here means a token that asks to be
 * verified with anything else — an unsigned `none`, or a symmetric algorithm
 * keyed on the public key — is rejected before any signature check happens.
 */
const ALLOWED_ALGORITHMS = ['RS256'] as const;

/** Signing keys change rarely; an hour of caching keeps JWKS off the hot path. */
const JWKS_TTL_SECONDS = 3600;

export interface Auth0Config {
	issuer: string;
	audience: string;
	jwksUri: string;
}

export interface AccessTokenClaims extends JWTPayload {
	sub: string;
	permissions?: string[];
	scope?: string;
}

export function auth0Config(env: Env): Auth0Config {
	const domain = (env.AUTH0_DOMAIN ?? '')
		.trim()
		.replace(/^https?:\/\//, '')
		.replace(/\/+$/, '');

	if (!domain) throw new HttpError(500, 'AUTH0_DOMAIN is not configured.');

	const audience = (env.AUTH0_AUDIENCE ?? '').trim();
	if (!audience) throw new HttpError(500, 'AUTH0_AUDIENCE is not configured.');

	return {
		// Auth0 issues tokens with a trailing slash on the issuer, and the claim
		// has to match exactly.
		issuer: `https://${domain}/`,
		audience,
		jwksUri: `https://${domain}/.well-known/jwks.json`,
	};
}

const jwksCacheKey = (issuer: string) => `jwks:v1:${issuer}`;

async function fetchJwks(config: Auth0Config): Promise<HonoJsonWebKey[]> {
	let response: Response;
	try {
		response = await fetch(config.jwksUri, { headers: { accept: 'application/json' } });
	} catch (error) {
		// Converted rather than left to propagate, so a DNS or TLS failure surfaces
		// as a deliberate 503 instead of an unhandled rejection.
		console.warn('JWKS fetch failed', error instanceof Error ? error.message : error);
		throw new HttpError(503, 'Could not reach the identity provider.');
	}

	if (!response.ok) throw new HttpError(503, 'Could not reach the identity provider.');

	const body = (await response.json()) as { keys?: HonoJsonWebKey[] };
	if (!Array.isArray(body.keys) || body.keys.length === 0) {
		throw new HttpError(503, 'The identity provider returned no signing keys.');
	}

	return body.keys;
}

/** Reads the signing keys, preferring the cached copy unless a refresh is forced. */
export async function readJwks(env: Env, config: Auth0Config, refresh = false): Promise<HonoJsonWebKey[]> {
	const key = jwksCacheKey(config.issuer);

	if (!refresh) {
		const cached = await env.CACHE.get<HonoJsonWebKey[]>(key, 'json');
		if (cached?.length) return cached;
	}

	const keys = await fetchJwks(config);
	await env.CACHE.put(key, JSON.stringify(keys), { expirationTtl: JWKS_TTL_SECONDS });
	return keys;
}

/**
 * Verifies an Auth0 access token: signature against the tenant's JWKS, plus the
 * issuer, audience and expiry claims.
 */
export async function verifyAccessToken(env: Env, token: string): Promise<AccessTokenClaims> {
	const config = auth0Config(env);

	// Read the key id first. Deciding up front whether the cached JWKS can even
	// contain this key keeps a malformed token from triggering a network fetch,
	// and keeps "we rotated keys" distinct from "this token is junk".
	let kid: string | undefined;
	try {
		const header = Jwt.decode(token).header as { kid?: string };
		kid = header.kid;
	} catch {
		throw unauthorized('Malformed access token.');
	}

	if (!kid) throw unauthorized('Access token has no key id.');

	let keys = await readJwks(env, config);
	if (!keys.some((key) => key.kid === kid)) {
		// The key is not in our copy, which is what a rotation looks like. Refetch
		// once; if it is still missing, or the provider is unreachable, the request
		// is denied rather than admitted unverified.
		keys = await readJwks(env, config, true);
	}

	let payload: JWTPayload;
	try {
		payload = await Jwt.verifyWithJwks(token, {
			keys,
			verification: { iss: config.issuer, aud: config.audience },
			allowedAlgorithms: [...ALLOWED_ALGORITHMS],
		});
	} catch (error) {
		throw unauthorizedFor(error);
	}

	if (typeof payload.sub !== 'string' || !payload.sub) {
		throw unauthorized('Access token has no subject.');
	}

	return payload as AccessTokenClaims;
}

/** Every verification failure is a 401; the reason goes to logs, not the client. */
function unauthorizedFor(error: unknown): HttpError {
	if (error instanceof HttpError) return error;
	console.warn('Access token rejected', error instanceof Error ? error.name : error);
	return unauthorized('Invalid or expired access token.');
}
