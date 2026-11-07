import { env } from 'cloudflare:test';
import { Jwt } from 'hono/utils/jwt';
import type { HonoJsonWebKey } from 'hono/utils/jwt/jws';

/** Must match the vars the pool binds in `vitest.server.config.ts`. */
export const TEST_DOMAIN = 'auth.test.example';
export const TEST_ISSUER = `https://${TEST_DOMAIN}/`;
export const TEST_AUDIENCE = 'https://api.kozeki.test';
export const TEST_SUBJECT = 'auth0|test-user';
/** A second, unrelated account, for proving one user cannot reach another's data. */
export const OTHER_SUBJECT = 'auth0|other-user';

const KEY_ID = 'test-signing-key';

interface TestKeys {
	privateJwk: HonoJsonWebKey;
	publicJwk: HonoJsonWebKey;
}

/**
 * One RSA keypair per test worker. Generating it is slow enough to be worth
 * doing once, and every test then signs against the same published JWKS.
 */
let keysPromise: Promise<TestKeys> | null = null;

function generateKeys(): Promise<TestKeys> {
	return (async () => {
		const pair = (await crypto.subtle.generateKey(
			{ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
			true,
			['sign', 'verify'],
		)) as CryptoKeyPair;

		const privateJwk = (await crypto.subtle.exportKey('jwk', pair.privateKey)) as HonoJsonWebKey;
		const publicJwk = (await crypto.subtle.exportKey('jwk', pair.publicKey)) as HonoJsonWebKey;

		// Hono copies `kid` and `alg` from the signing key into the JWT header,
		// which is what lets the verifier pick this key out of the JWKS.
		return {
			privateJwk: { ...privateJwk, kid: KEY_ID, alg: 'RS256' },
			publicJwk: { ...publicJwk, kid: KEY_ID, alg: 'RS256', use: 'sig' },
		};
	})();
}

export function testKeys(): Promise<TestKeys> {
	keysPromise ??= generateKeys();
	return keysPromise;
}

/**
 * Seeds the cache with this keypair's public half, so token verification reads
 * the JWKS from KV instead of reaching for the network.
 */
export async function publishJwks(keys?: HonoJsonWebKey[]): Promise<void> {
	const { publicJwk } = await testKeys();
	await env.CACHE.put(`jwks:v1:${TEST_ISSUER}`, JSON.stringify(keys ?? [publicJwk]));
}

export interface TokenOverrides {
	sub?: string;
	iss?: string;
	aud?: string | string[];
	/** Seconds from now until the token expires; negative mints an expired one. */
	expiresIn?: number;
	permissions?: string[];
}

/** Mints an access token shaped like the ones Auth0 issues. */
export async function mintToken(overrides: TokenOverrides = {}): Promise<string> {
	const { privateJwk } = await testKeys();
	const now = Math.floor(Date.now() / 1000);

	return await Jwt.sign(
		{
			sub: overrides.sub ?? TEST_SUBJECT,
			iss: overrides.iss ?? TEST_ISSUER,
			aud: overrides.aud ?? TEST_AUDIENCE,
			iat: now - 5,
			nbf: now - 5,
			exp: now + (overrides.expiresIn ?? 3600),
			...(overrides.permissions ? { permissions: overrides.permissions } : {}),
		},
		privateJwk,
		'RS256',
	);
}
