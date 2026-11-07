/**
 * The synced tables, described once for the Worker and the web app alike.
 *
 * A row travels in the shape the Android app's Room entity serialises to: its fields flat, camelCase,
 * with the bookkeeping in a nested `sync` object. That is also the shape of a Kozeki backup, so the
 * phone sends its rows as they are. The Worker stores each field in a snake_case column of the same
 * name (`seriesNumber` → `series_number`), which `columnOf` works out, and migrations must keep to it;
 * `test/schema.spec.ts` checks that every field here has its column.
 *
 * Comics never reach the server: the phone leaves out books whose source is COMICS, their chapters,
 * their AniList tracks, and anything else that hangs off them. Comic-only columns of `books`
 * (`sourceMemo`, `newChapters`, `chaptersCheckedAt`) are not listed, so they are dropped on the way in.
 */

export type FieldKind = 'text' | 'int' | 'real' | 'bool' | 'json';

export interface Field {
	readonly name: string;
	readonly kind: FieldKind;
	/** A row without it is refused. Key fields are always required. */
	readonly required?: boolean;
}

export interface TableSpec {
	readonly name: TableName;
	/** The fields that identify a row within one user's data. */
	readonly key: readonly string[];
	readonly fields: readonly Field[];
}

export const TABLE_NAMES = [
	'books',
	'readThroughs',
	'readingSessions',
	'physicalReadings',
	'notes',
	'yearlyGoals',
	'shelves',
	'shelfBooks',
	'settings',
] as const;

export type TableName = (typeof TABLE_NAMES)[number];

const text = (name: string, required = false): Field => ({ name, kind: 'text', required });
const int = (name: string, required = false): Field => ({ name, kind: 'int', required });
const real = (name: string, required = false): Field => ({ name, kind: 'real', required });
const bool = (name: string): Field => ({ name, kind: 'bool' });
const json = (name: string): Field => ({ name, kind: 'json' });

/** In push order: a row's parent table comes before it, so a parent is never missing for long. */
export const TABLES: Record<TableName, TableSpec> = {
	books: {
		name: 'books',
		key: ['id'],
		fields: [
			text('id', true),
			text('title', true),
			text('subtitle'),
			json('authors'),
			json('authorRefs'),
			text('description'),
			text('publisher'),
			text('series'),
			real('seriesNumber'),
			text('publishedDate'),
			text('language'),
			int('pageCount'),
			text('isbn10'),
			text('isbn13'),
			json('categories'),
			text('coverFile'),
			text('coverUrl'),
			bool('customCover'),
			bool('detailsEdited'),
			text('state'),
			text('acquisition'),
			bool('isFavorite'),
			real('rating'),
			int('purchasePriceMinor'),
			text('purchaseCurrency'),
			int('purchasedOn'),
			text('purchaseLocation'),
			text('purchaseMemo'),
			text('source'),
			text('sourceId'),
			text('sourceUrl'),
			text('matchStatus'),
			text('fileHash'),
			int('fileSize'),
			text('originalFileName'),
			text('locator'),
			real('progression'),
			int('position'),
			int('positionCount'),
			text('chapterTitle'),
			int('chapterIndex'),
			int('chapterCount'),
			int('lastReadAt'),
			int('physicalPage'),
			int('physicalPageCount'),
		],
	},
	readThroughs: {
		name: 'readThroughs',
		key: ['id'],
		fields: [
			text('id', true),
			text('bookId', true),
			int('number', true),
			int('startedAt', true),
			int('finishedAt'),
			text('outcome'),
			int('finishedOn'),
		],
	},
	readingSessions: {
		name: 'readingSessions',
		key: ['id'],
		fields: [
			text('id', true),
			text('bookId', true),
			text('readThroughId'),
			int('startedAt', true),
			int('endedAt', true),
			int('durationMs', true),
			int('day', true),
			real('startProgression', true),
			real('endProgression', true),
			int('startPosition'),
			int('endPosition'),
			int('pages'),
			int('chapters'),
			text('startChapter'),
			text('endChapter'),
		],
	},
	physicalReadings: {
		name: 'physicalReadings',
		key: ['id'],
		fields: [
			text('id', true),
			text('bookId', true),
			text('readThroughId'),
			int('recordedAt', true),
			int('day', true),
			int('startPage', true),
			int('endPage', true),
		],
	},
	notes: {
		name: 'notes',
		key: ['id'],
		fields: [text('id', true), text('bookId', true), text('text', true), text('chapterTitle'), real('progression')],
	},
	yearlyGoals: {
		name: 'yearlyGoals',
		key: ['year'],
		fields: [int('year', true), int('books', true)],
	},
	shelves: {
		name: 'shelves',
		key: ['id'],
		fields: [text('id', true), text('name', true)],
	},
	shelfBooks: {
		name: 'shelfBooks',
		key: ['shelfId', 'bookId'],
		fields: [text('shelfId', true), text('bookId', true)],
	},
	/** The few settings both apps share, one row per setting, its value as JSON. */
	settings: {
		name: 'settings',
		key: ['key'],
		fields: [text('key', true), json('value')],
	},
};

/** `seriesNumber` → `series_number`. */
export function columnOf(field: string): string {
	return field.replace(/[A-Z0-9]+/g, (match) => `_${match.toLowerCase()}`);
}

/** `readThroughs` → `read_throughs`. */
export const tableOf = (name: TableName): string => columnOf(name);

/** The row bookkeeping, as Room's `SyncStamp` carries it. `syncedAt` is the phone's own and never stored. */
export interface SyncStamp {
	createdAt: number;
	updatedAt: number;
	deletedAt?: number | null;
}

export type Row = Record<string, unknown> & { sync: SyncStamp };

/** The settings both apps share. Anything else stays on the device that set it. */
export const SHARED_SETTINGS = ['dailyGoalMinutes', 'lastCurrency'] as const;

/** What `POST /api/sync` answers. */
export interface SyncResponse {
	rows: Partial<Record<TableName, Row[]>>;
	/** Pass back as `cursor` next time. */
	cursor: number;
	/** More rows are waiting past `cursor`. */
	more: boolean;
}

/**
 * The fields of a book the reader keeps, which change on nearly every page turn. They win by
 * `lastReadAt`, apart from the rest of the book, which wins by `updatedAt`: a page turned on the
 * phone and a rating given on the web, each from a copy that had not seen the other, both stay.
 * Writing them alone moves `lastReadAt` and not `updatedAt`; anything else that changes them
 * (finishing a book, reading it again) moves both.
 */
export const PROGRESS_FIELDS = [
	'locator',
	'progression',
	'position',
	'positionCount',
	'chapterTitle',
	'chapterIndex',
	'chapterCount',
	'lastReadAt',
] as const;

/**
 * What a cover file may be called: a plain file name, never a path. The phone saves a book's cover
 * under one of these, and writes the file it is sent under the same name, so the server refuses
 * anything else for `coverFile`.
 */
export const COVER_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.(jpe?g|png|webp)$/;

interface BookClocks {
	lastReadAt?: number | null;
	sync: SyncStamp;
}

/**
 * [local] and [remote] put together: the reader's fields from whichever was read later, the rest
 * from whichever was edited later. `fromRemote` says whether the result is the remote row as it
 * came, so nothing of [local] is left to send. Null when [local] wins on both counts.
 */
export function mergeBook<B extends BookClocks>(local: B | undefined, remote: B): { book: B; fromRemote: boolean } | null {
	if (!local) return { book: remote, fromRemote: true };
	const details = remote.sync.updatedAt >= local.sync.updatedAt;
	const progress = (remote.lastReadAt ?? 0) >= (local.lastReadAt ?? 0);
	if (!details && !progress) return null;
	if (details && progress) return { book: remote, fromRemote: true };
	const base = details ? remote : local;
	const reader = progress ? remote : local;
	const book = { ...base } as Record<string, unknown>;
	for (const field of PROGRESS_FIELDS) book[field] = (reader as unknown as Record<string, unknown>)[field];
	book.sync = { ...base.sync, updatedAt: Math.max(local.sync.updatedAt, remote.sync.updatedAt) };
	return { book: book as B, fromRemote: false };
}
