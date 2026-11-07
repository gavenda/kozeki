import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';
import { TABLE_NAMES, TABLES, columnOf, tableOf } from '../shared/tables';

describe('schema', () => {
	it.each(TABLE_NAMES)('has a column for every field of %s', async (name) => {
		const { results } = await env.DB.prepare(`PRAGMA table_info(${tableOf(name)})`).all<{ name: string }>();
		const columns = results.map((column) => column.name);
		for (const field of TABLES[name].fields) expect(columns).toContain(columnOf(field.name));
		expect(columns).toEqual(expect.arrayContaining(['user_id', 'created_at', 'updated_at', 'deleted_at', 'rev']));
	});
});
