import { describe, expect, it } from 'vitest';
import { authedClient, json, otherClient } from './helpers';

const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3]);

describe('/api/covers', () => {
	it('stores a cover and gives it back', async () => {
		const call = await authedClient();
		const put = await call('/covers/b1-1.jpg', { method: 'PUT', body: jpeg, headers: { 'content-type': 'image/jpeg' } });
		expect(put.status).toBe(204);

		const get = await call('/covers/b1-1.jpg');
		expect(get.status).toBe(200);
		expect(get.headers.get('content-type')).toBe('image/jpeg');
		expect(new Uint8Array(await get.arrayBuffer())).toEqual(jpeg);
	});

	it('lists the covers it does not have', async () => {
		const call = await authedClient();
		await call('/covers/b1-1.jpg', { method: 'PUT', body: jpeg });
		const response = await call('/covers/missing', {
			method: 'POST',
			body: JSON.stringify({ names: ['b1-1.jpg', 'b2-2.jpg', '../x.jpg'] }),
		});
		expect(await json(response)).toEqual({ missing: ['b2-2.jpg'] });
	});

	it('refuses names that are not cover files', async () => {
		const call = await authedClient();
		expect((await call('/covers/notes.txt', { method: 'PUT', body: jpeg })).status).toBe(400);
		expect((await call('/covers/b1-1.jpg', { method: 'PUT', body: new Uint8Array() })).status).toBe(400);
	});

	it("keeps one user's covers from another", async () => {
		const mine = await authedClient();
		const theirs = await otherClient();
		await mine('/covers/b1-1.jpg', { method: 'PUT', body: jpeg });
		expect((await theirs('/covers/b1-1.jpg')).status).toBe(404);
	});
});
