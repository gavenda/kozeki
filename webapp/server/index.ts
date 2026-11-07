import { Hono } from 'hono';
import { HTTPException } from 'hono/http-exception';
import { HttpError } from './errors';
import { bookRoutes } from './routes/books';
import { coverRoutes } from './routes/covers';
import { hardcoverRoutes } from './routes/hardcover';
import { syncRoutes } from './routes/sync';
import type { AppEnv } from './types';

/**
 * The Worker's entry point (`main` in `wrangler.jsonc`). `assets.run_worker_first` sends
 * `/api/*` here; everything else is answered by the static assets. The API and the web app
 * share an origin, so there is no CORS layer. The Android app is not a browser and needs none.
 */
const app = new Hono<AppEnv>();

app.get('/api/health', (c) => c.json({ status: 'ok', service: 'kozeki' }));

app.route('/api/sync', syncRoutes);
app.route('/api/covers', coverRoutes);
app.route('/api/hardcover', hardcoverRoutes);
app.route('/api/books', bookRoutes);

app.notFound((c) => c.json({ error: 'Not found.' }, 404));

app.onError((error, c) => {
	if (error instanceof HttpError) {
		return c.json({ error: error.message, ...(error.details ? { details: error.details } : {}) }, error.status);
	}
	if (error instanceof HTTPException) return error.getResponse();

	console.error('Unhandled error', error);
	return c.json({ error: 'Internal server error.' }, 500);
});

export default { fetch: app.fetch } satisfies ExportedHandler<Env>;
