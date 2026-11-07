import { z } from 'zod';
import {
	TABLE_NAMES,
	TABLES,
	columnOf,
	tableOf,
	type Field,
	type Row,
	type SyncResponse,
	type TableName,
	type TableSpec,
	COVER_NAME,
	PROGRESS_FIELDS,
} from '../shared/tables';

export type { SyncResponse };

/**
 * One request's worth of rows each way. The phone pushes in chunks well under the
 * cap, and pulls until `more` is false.
 */
export const MAX_PUSH_ROWS = 500;
/** A request body larger than this is refused before it is read. */
export const MAX_BODY_BYTES = 4 * 1024 * 1024;
export const DEFAULT_PULL_LIMIT = 1000;
const MAX_TEXT = 200_000;

const stampSchema = z.object({
	createdAt: z.number().int().nonnegative(),
	updatedAt: z.number().int().nonnegative(),
	deletedAt: z.number().int().nonnegative().nullish(),
	// The phone's own bookkeeping, sent along because it is part of its row. Never stored.
	syncedAt: z.number().int().nullish(),
});

function fieldSchema(field: Field): z.ZodType {
	const base = {
		text: z.string().max(MAX_TEXT),
		int: z.number().int().refine(Number.isSafeInteger, 'Out of range.'),
		real: z.number().finite(),
		bool: z.boolean(),
		json: z.unknown().refine((value) => value === undefined || JSON.stringify(value).length <= MAX_TEXT, 'Too large.'),
	}[field.kind];
	return field.required ? base : base.nullish();
}

function rowSchema(spec: TableSpec) {
	const shape: Record<string, z.ZodType> = { sync: stampSchema };
	for (const field of spec.fields) shape[field.name] = fieldSchema(field);
	const schema = z.object(shape);
	// Comics stay on the phone. A comic arriving here means the phone's filter broke, which
	// should fail loudly rather than fill the web library with them.
	if (spec.name !== 'books') return schema;
	return schema
		.refine((row) => row.source !== 'COMICS' && row.source !== 'MANGA', { message: 'Comics are not synced.', path: ['source'] })
		.refine((row) => row.coverFile == null || COVER_NAME.test(row.coverFile as string), {
			message: 'Not a cover file name.',
			path: ['coverFile'],
		});
}

export const syncRequestSchema = z.object({
	/** The highest `rev` this device has already pulled; 0 for none. */
	cursor: z.number().int().nonnegative(),
	push: z
		.object(Object.fromEntries(TABLE_NAMES.map((name) => [name, z.array(rowSchema(TABLES[name])).optional()])))
		.partial()
		.optional(),
	limit: z.number().int().min(1).max(DEFAULT_PULL_LIMIT).optional(),
});

export type SyncRequest = z.infer<typeof syncRequestSchema>;

const STAMP_COLUMNS = ['created_at', 'updated_at', 'deleted_at', 'rev'];

/** A field's value as D1 stores it. */
function toColumn(field: Field, value: unknown): unknown {
	if (value === undefined || value === null) {
		// Booleans are NOT NULL; JSON lists read back as empty rather than missing.
		return field.kind === 'bool' ? 0 : null;
	}
	if (field.kind === 'bool') return value ? 1 : 0;
	if (field.kind === 'json') return JSON.stringify(value);
	return value;
}

/** A stored row in the shape the apps send, leaving out what is null so their defaults apply. */
export function toWire(spec: TableSpec, record: Record<string, unknown>): Row {
	const row: Record<string, unknown> = {};
	for (const field of spec.fields) {
		const value = record[columnOf(field.name)];
		if (value === null || value === undefined) continue;
		if (field.kind === 'bool') row[field.name] = value === 1;
		else if (field.kind === 'json') row[field.name] = JSON.parse(value as string);
		else row[field.name] = value;
	}
	const sync: Row['sync'] = { createdAt: record.created_at as number, updatedAt: record.updated_at as number };
	if (record.deleted_at != null) sync.deletedAt = record.deleted_at as number;
	return { ...row, sync } as Row;
}

// D1 holds a value of up to 2 MB, and each table's rows go in as one JSON value, so a table with
// more than this much is written in several statements.
const MAX_JSON_CHUNK = 800_000;

/** A pushed row in the shape of its table's columns, as the JSON each statement reads it from. */
function toColumns(spec: TableSpec, row: Row, revFromEnd: number): Record<string, unknown> {
	const record: Record<string, unknown> = {};
	for (const field of spec.fields) record[columnOf(field.name)] = toColumn(field, row[field.name]);
	record.created_at = row.sync.createdAt;
	record.updated_at = row.sync.updatedAt;
	record.deleted_at = row.sync.deletedAt ?? null;
	record.rev_from_end = revFromEnd;
	return record;
}

const field = (column: string) => `json_extract(j.value, '$.${column}')`;
const PROGRESS_COLUMNS: readonly string[] = PROGRESS_FIELDS.map(columnOf);

/**
 * Writes a table's rows from one JSON array, each unless the server holds a newer version of it.
 * The later edit wins, judged by `updated_at`; for a book, its reader's columns win apart by
 * `last_read_at` (see `PROGRESS_FIELDS`). Each row takes its `rev` from the block the batch
 * reserved, counting back from the user's new counter.
 */
function upsert(db: D1Database, spec: TableSpec, userId: string, json: string): D1PreparedStatement {
	const table = tableOf(spec.name);
	const columns = spec.fields.map((f) => columnOf(f.name));
	const keys = spec.key.map(columnOf);
	const values = columns.filter((column) => !keys.includes(column));
	const newer = `excluded.updated_at > ${table}.updated_at`;
	const readLater = `COALESCE(excluded.last_read_at, 0) > COALESCE(${table}.last_read_at, 0)`;
	const isBook = spec.name === 'books';

	const updates = isBook
		? [
				...values.map((column) => {
					const wins = PROGRESS_COLUMNS.includes(column) ? readLater : newer;
					return `${column} = CASE WHEN ${wins} THEN excluded.${column} ELSE ${table}.${column} END`;
				}),
				`deleted_at = CASE WHEN ${newer} THEN excluded.deleted_at ELSE ${table}.deleted_at END`,
				`updated_at = MAX(excluded.updated_at, ${table}.updated_at)`,
				'rev = excluded.rev',
			]
		: [...values, 'updated_at', 'deleted_at', 'rev'].map((column) => `${column} = excluded.${column}`);

	// `WHERE true` is what lets SQLite tell the upsert's ON CONFLICT from a join's.
	const sql =
		`INSERT INTO ${table} (user_id, ${[...columns, ...STAMP_COLUMNS].join(', ')}) ` +
		`SELECT ?1, ${[...columns, 'created_at', 'updated_at', 'deleted_at'].map(field).join(', ')}, ` +
		`(SELECT rev FROM users WHERE id = ?1) - ${field('rev_from_end')} ` +
		`FROM json_each(?2) AS j WHERE true ` +
		`ON CONFLICT (user_id, ${keys.join(', ')}) DO UPDATE SET ${updates.join(', ')} ` +
		`WHERE ${isBook ? `${newer} OR ${readLater}` : newer}`;

	return db.prepare(sql).bind(userId, json);
}

/**
 * The stored version of each pushed row that the server did not take as it was sent, because it
 * held something newer. Run in the same batch, after the writes.
 */
function keptVersions(db: D1Database, spec: TableSpec, userId: string, json: string): D1PreparedStatement {
	const table = tableOf(spec.name);
	const join = spec.key.map(columnOf).map((key) => `t.${key} = ${field(key)}`);
	const differs = [`t.updated_at != ${field('updated_at')}`];
	if (spec.name === 'books') differs.push(`COALESCE(t.last_read_at, 0) != COALESCE(${field('last_read_at')}, 0)`);
	return db
		.prepare(
			`SELECT t.* FROM ${table} AS t JOIN json_each(?2) AS j ON t.user_id = ?1 AND ${join.join(' AND ')} WHERE ${differs.join(' OR ')}`,
		)
		.bind(userId, json);
}

/** Splits a table's rows into JSON arrays small enough for one D1 value each. */
function jsonChunks(records: Record<string, unknown>[]): string[] {
	const chunks: string[] = [];
	let current: string[] = [];
	let size = 0;
	for (const record of records) {
		const text = JSON.stringify(record);
		if (current.length && size + text.length > MAX_JSON_CHUNK) {
			chunks.push(`[${current.join(',')}]`);
			current = [];
			size = 0;
		}
		current.push(text);
		size += text.length + 1;
	}
	if (current.length) chunks.push(`[${current.join(',')}]`);
	return chunks;
}

/**
 * Applies a push in one D1 batch, which is one transaction: the counter moves and every row takes
 * its number in the same write, so no pull can ever see a later number land before an earlier one.
 * A table's rows go in one statement however many there are, which keeps a request within D1's
 * limit on queries.
 *
 * @returns the server's version of every pushed row it did not take whole, because it held
 *   something newer. The device has marked its own as sent, so it needs these back to catch up.
 */
export async function applyPush(db: D1Database, userId: string, push: SyncRequest['push']): Promise<Partial<Record<TableName, Row[]>>> {
	const total = pushSize(push);
	if (total === 0) return {};

	let index = 0;
	const parts: { spec: TableSpec; json: string }[] = [];
	for (const name of TABLE_NAMES) {
		const rows = (push?.[name] ?? []) as Row[];
		if (rows.length === 0) continue;
		const records = rows.map((row) => toColumns(TABLES[name], row, total - 1 - index++));
		for (const json of jsonChunks(records)) parts.push({ spec: TABLES[name], json });
	}

	const results = await db.batch([
		db.prepare('UPDATE users SET rev = rev + ? WHERE id = ?').bind(total, userId),
		...parts.map(({ spec, json }) => upsert(db, spec, userId, json)),
		...parts.map(({ spec, json }) => keptVersions(db, spec, userId, json)),
	]);

	const rows: Partial<Record<TableName, Row[]>> = {};
	parts.forEach(({ spec }, part) => {
		for (const record of results[1 + parts.length + part].results as Record<string, unknown>[]) {
			(rows[spec.name] ??= []).push(toWire(spec, record));
		}
	});
	return rows;
}

/**
 * The rows changed after `cursor`, oldest first, at most `limit` of them.
 *
 * Each table is asked for one more than the limit: whatever the first `limit` rows across all
 * tables are, each table's share of them is among its own first `limit + 1`.
 */
export async function pull(db: D1Database, userId: string, cursor: number, limit: number): Promise<SyncResponse> {
	const answers = await db.batch(
		TABLE_NAMES.map((name) =>
			db.prepare(`SELECT * FROM ${tableOf(name)} WHERE user_id = ? AND rev > ? ORDER BY rev LIMIT ?`).bind(userId, cursor, limit + 1),
		),
	);

	const changed = TABLE_NAMES.flatMap((name, index) =>
		(answers[index].results as Record<string, unknown>[]).map((record) => ({ name, rev: record.rev as number, record })),
	).sort((a, b) => a.rev - b.rev);

	const taken = changed.slice(0, limit);
	const rows: Partial<Record<TableName, Row[]>> = {};
	for (const { name, record } of taken) (rows[name] ??= []).push(toWire(TABLES[name], record));

	return {
		rows,
		cursor: taken.length > 0 ? taken[taken.length - 1].rev : cursor,
		more: changed.length > limit,
	};
}

/** Merges rows the push kept into what the pull returned, without sending one twice. */
export function mergeRows(
	pulled: Partial<Record<TableName, Row[]>>,
	kept: Partial<Record<TableName, Row[]>>,
): Partial<Record<TableName, Row[]>> {
	const merged = { ...pulled };
	for (const name of TABLE_NAMES) {
		const extra = kept[name];
		if (!extra?.length) continue;
		const spec = TABLES[name];
		const keyOf = (row: Row) => spec.key.map((key) => String(row[key])).join('\u0000');
		const seen = new Set((merged[name] ?? []).map(keyOf));
		merged[name] = [...(merged[name] ?? []), ...extra.filter((row) => !seen.has(keyOf(row)))];
	}
	return merged;
}

export function pushSize(push: SyncRequest['push']): number {
	return TABLE_NAMES.reduce((sum, name) => sum + (push?.[name]?.length ?? 0), 0);
}
