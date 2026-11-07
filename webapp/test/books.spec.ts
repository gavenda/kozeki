import { describe, expect, it } from 'vitest';
import { authedClient, request } from './helpers';

describe('/api/books', () => {
	it('needs a signed-in caller', async () => {
		expect((await request('/books/volumes/abc')).status).toBe(401);
	});

	it('refuses what cannot be a Google Books search or volume before asking Google', async () => {
		const call = await authedClient();
		expect((await call('/books/search?q=dune&start=-1')).status).toBe(400);
		expect((await call('/books/search?q=dune&start=x')).status).toBe(400);
		expect((await call('/books/volumes/not%20an%20id')).status).toBe(400);
	});
});
