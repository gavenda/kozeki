import { Hono } from 'hono';
import { z } from 'zod';
import { COVER_NAME } from '../../shared/tables';
import { badRequest, limitBody, notFound } from '../errors';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';
import { parseJson } from '../validate';

/**
 * Cover pictures, kept in R2 under `covers/<subject>/<file>`.
 *
 * A book names its cover by file (`coverFile`), and the phone gives every saved cover a new
 * name, so an object is never overwritten: a changed cover is a new file, and caching one
 * for good is safe.
 */
const MAX_BYTES = 5 * 1024 * 1024;
const TYPES: Record<string, string> = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

function objectKey(userId: string, name: string): string {
	if (!COVER_NAME.test(name)) throw badRequest('Not a cover file name.');
	return `covers/${userId}/${name}`;
}

const missingSchema = z.object({ names: z.array(z.string()).max(200) });

export const coverRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	/** Which of these the server does not have yet, so the phone uploads only those. */
	.post('/missing', limitBody(64 * 1024), async (c) => {
		const { names } = await parseJson(c, missingSchema);
		const userId = c.get('userId');
		const valid = names.filter((name) => COVER_NAME.test(name));
		const present = await Promise.all(valid.map((name) => c.env.COVERS.head(objectKey(userId, name))));
		return c.json({ missing: valid.filter((_, index) => present[index] === null) });
	})
	.put('/:name', limitBody(MAX_BYTES), async (c) => {
		const name = c.req.param('name');
		const key = objectKey(c.get('userId'), name);
		const body = await c.req.arrayBuffer();
		if (body.byteLength === 0) throw badRequest('The picture is empty.');
		if (body.byteLength > MAX_BYTES) throw badRequest('The picture is too large.');
		const extension = name.split('.').pop()!.toLowerCase();
		await c.env.COVERS.put(key, body, { httpMetadata: { contentType: TYPES[extension] } });
		return c.body(null, 204);
	})
	.get('/:name', async (c) => {
		const object = await c.env.COVERS.get(objectKey(c.get('userId'), c.req.param('name')));
		if (!object) throw notFound();
		return new Response(object.body, {
			headers: {
				'content-type': object.httpMetadata?.contentType ?? 'application/octet-stream',
				'cache-control': 'private, max-age=31536000, immutable',
			},
		});
	});
