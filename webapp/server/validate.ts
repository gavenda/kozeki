import type { Context } from 'hono';
import { z } from 'zod';
import { badRequest } from './errors';

/** Parses and validates a JSON request body, raising a 400 with field details. */
export async function parseJson<S extends z.ZodType>(c: Context, schema: S): Promise<z.infer<S>> {
	let raw: unknown;
	try {
		raw = await c.req.json();
	} catch {
		throw badRequest('Request body must be valid JSON.');
	}

	const result = schema.safeParse(raw);
	if (!result.success) throw badRequest('Invalid request body.', z.flattenError(result.error).fieldErrors);
	return result.data;
}
