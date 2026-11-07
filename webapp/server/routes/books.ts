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

async function fromGoogle(c: { env: AppEnv['Bindings'] }, url: URL, what: string): Promise<Response> {
	if (c.env.GOOGLE_BOOKS_API_KEY) url.searchParams.set('key', c.env.GOOGLE_BOOKS_API_KEY);
	let upstream: Response;
	try {
		upstream = await fetch(url, { headers: { accept: 'application/json' } });
	} catch {
		throw new HttpError(502, 'Could not reach Google Books.');
	}
	if (upstream.status === 429) throw new HttpError(429, 'Too many lookups. Try again later.');
	if (upstream.status === 404 || upstream.status === 503) throw new HttpError(404, 'Google Books has no such book.');
	if (!upstream.ok) {
		console.warn('Google Books answered', upstream.status, await upstream.text().catch(() => ''));
		throw new HttpError(502, `Google Books refused the ${what} (${upstream.status}).`);
	}
	return new Response(upstream.body, { headers: { 'content-type': 'application/json' } });
}

export const bookRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/search', async (c) => {
		const query = c.req.query('q')?.trim() ?? '';
		if (!query || query.length > 200) throw badRequest('A search of 1 to 200 characters is needed.');
		const start = Number(c.req.query('start') ?? '0');
		if (!Number.isInteger(start) || start < 0 || start > 1000) throw badRequest('The start must be a whole number from 0 to 1000.');

		const url = new URL(VOLUMES);
		url.search = new URLSearchParams({ q: query, maxResults: '20', startIndex: String(start), printType: 'books' }).toString();
		return fromGoogle(c, url, 'search');
	})
	// One volume, for a book found earlier whose page is opened again.
	.get('/volumes/:id', async (c) => {
		const id = c.req.param('id');
		if (!/^[\w-]{1,40}$/.test(id)) throw badRequest('Not a Google Books volume ID.');
		return fromGoogle(c, new URL(`${VOLUMES}/${id}`), 'lookup');
	});
