import { Hono } from 'hono';
import { badRequest, limitBody } from '../errors';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';

/**
 * Hardcover for the web app, which signs in to it with its own OAuth client (authorization
 * code with PKCE, no secret) and keeps its token in the browser.
 *
 * Hardcover's token endpoint and API are not open to calls from another origin, so the
 * browser's requests pass through here unchanged. Nothing is stored: the form for the token
 * endpoint and the Hardcover token for the API are the browser's own, and forwarding them
 * from a public client gives nobody anything they could not do directly.
 */
const TOKEN_ENDPOINT = 'https://api.hardcover.app/oauth2/token';
const REVOKE_ENDPOINT = 'https://api.hardcover.app/oauth2/revoke';
const GRAPHQL_ENDPOINT = 'https://api.hardcover.app/v1/graphql';

async function forward(url: string, init: RequestInit): Promise<Response> {
	let upstream: Response;
	try {
		upstream = await fetch(url, init);
	} catch {
		return Response.json({ error: 'Could not reach Hardcover.' }, { status: 502 });
	}
	return new Response(upstream.body, {
		status: upstream.status,
		headers: { 'content-type': upstream.headers.get('content-type') ?? 'application/json' },
	});
}

const formInit = (body: string): RequestInit => ({
	method: 'POST',
	headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
	body,
});

export const hardcoverRoutes = new Hono<AppEnv>()
	.use('*', requireAuth, limitBody(64 * 1024))
	.post('/token', async (c) => forward(TOKEN_ENDPOINT, formInit(await c.req.text())))
	.post('/revoke', async (c) => forward(REVOKE_ENDPOINT, formInit(await c.req.text())))
	.post('/graphql', async (c) => {
		const token = c.req.header('X-Hardcover-Authorization');
		if (!token) throw badRequest('No Hardcover token.');
		return forward(GRAPHQL_ENDPOINT, {
			method: 'POST',
			headers: { 'content-type': 'application/json', authorization: token },
			body: await c.req.text(),
		});
	});
