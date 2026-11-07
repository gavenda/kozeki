import { Hono } from 'hono';
import { badRequest, limitBody } from '../errors';
import { requireAuth } from '../middleware/auth';
import {
	applyPush,
	DEFAULT_PULL_LIMIT,
	MAX_BODY_BYTES,
	MAX_PUSH_ROWS,
	mergeRows,
	pull,
	pushSize,
	syncRequestSchema,
	type SyncResponse,
} from '../sync';
import type { AppEnv } from '../types';
import { parseJson } from '../validate';

/**
 * `POST /api/sync`: the one way rows go in or out, for the phone and the web alike.
 *
 * The push is applied first, so the pull that follows already reflects it. A pushed row the
 * server kept its own newer version of comes back with the pull, so the sender catches up.
 */
export const syncRoutes = new Hono<AppEnv>().use('*', requireAuth, limitBody(MAX_BODY_BYTES)).post('/', async (c) => {
	const body = await parseJson(c, syncRequestSchema);
	if (pushSize(body.push) > MAX_PUSH_ROWS) throw badRequest(`At most ${MAX_PUSH_ROWS} rows can be pushed at once.`);

	const userId = c.get('userId');
	const kept = await applyPush(c.env.DB, userId, body.push);
	const pulled = await pull(c.env.DB, userId, body.cursor, body.limit ?? DEFAULT_PULL_LIMIT);

	return c.json<SyncResponse>({ ...pulled, rows: mergeRows(pulled.rows, kept) });
});
