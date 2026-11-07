import { Hono } from 'hono';
import { badRequest, HttpError } from '../errors';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';

/**
 * Google Books search for the web app. Keyless requests share a small allowance per address and
 * soon answer 429, so the search goes through here, where the key (`GOOGLE_BOOKS_API_KEY`, a
 * Worker secret) is added and never reaches the browser. Without the secret it searches keyless.
 */
const VOLUMES = 'https://www.googleapis.com/books/v1/volumes';

export const bookRoutes = new Hono<AppEnv>().use('*', requireAuth).get('/search', async (c) => {
	const query = c.req.query('q')?.trim() ?? '';
	if (!query || query.length > 200) throw badRequest('A search of 1 to 200 characters is needed.');

	const url = new URL(VOLUMES);
	url.search = new URLSearchParams({ q: query, maxResults: '20', printType: 'books' }).toString();
	if (c.env.GOOGLE_BOOKS_API_KEY) url.searchParams.set('key', c.env.GOOGLE_BOOKS_API_KEY);

	let upstream: Response;
	try {
		upstream = await fetch(url, { headers: { accept: 'application/json' } });
	} catch {
		throw new HttpError(502, 'Could not reach Google Books.');
	}
	if (upstream.status === 429) throw new HttpError(429, 'Too many lookups. Try again later.');
	if (!upstream.ok) {
		console.warn('Google Books answered', upstream.status, await upstream.text().catch(() => ''));
		throw new HttpError(502, `Google Books refused the search (${upstream.status}).`);
	}
	return new Response(upstream.body, { headers: { 'content-type': 'application/json' } });
});
