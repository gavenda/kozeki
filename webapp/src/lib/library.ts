import { TABLES, type TableName } from '../../shared/tables';
import type {
	Acquisition,
	Book,
	Changes,
	Note,
	PhysicalReading,
	ReadingState,
	ReadThrough,
	Rows,
	Setting,
	Shelf,
	ShelfBook,
	YearlyGoal,
} from '@/types';
import { epochDayOf } from './dates';

/**
 * The library's write rules, the same as the Android app's `LibraryRepository` and friends, so an
 * edit made here leaves the rows as one made on the phone would. Each operation runs in a [Tx] over
 * the rows as they are and returns the rows it changed, stamped, ready to push.
 */

type RowOf<T extends TableName> = Rows[T][number];

export type Tables = { [T in TableName]: Map<string, RowOf<T>> };

/** A row's key within its table, as the server keys it. */
export function keyOf<T extends TableName>(table: T, row: RowOf<T>): string {
	return TABLES[table].key.map((field) => String((row as unknown as Record<string, unknown>)[field])).join('|');
}

export const alive = (row: { sync: { deletedAt?: number | null } }): boolean => row.sync.deletedAt == null;

export const FINISHED_STATES: ReadingState[] = ['COMPLETED', 'DROPPED'];

/** Whether the book has an EPUB on some device, which is what puts it in the Library rather than the wishlist. */
export const inLibrary = (book: Book): boolean => book.fileHash != null;

export function newId(): string {
	return crypto.randomUUID();
}

/** Reads the rows with this operation's own changes on top, and collects those changes. */
export class Tx {
	private readonly changed: { [T in TableName]?: Map<string, RowOf<T>> } = {};
	private readonly base: Tables;
	readonly now: number;

	constructor(base: Tables, now: number = Date.now()) {
		this.base = base;
		this.now = now;
	}

	get<T extends TableName>(table: T, key: string): RowOf<T> | undefined {
		return (this.changed[table]?.get(key) ?? this.base[table].get(key)) as RowOf<T> | undefined;
	}

	/** Every live row of [table] matching [test], changes included. */
	all<T extends TableName>(table: T, test: (row: RowOf<T>) => boolean = () => true): RowOf<T>[] {
		const merged = new Map<string, RowOf<T>>(this.base[table] as Map<string, RowOf<T>>);
		for (const [key, row] of (this.changed[table] ?? new Map()) as Map<string, RowOf<T>>) merged.set(key, row);
		return [...merged.values()].filter((row) => alive(row) && test(row));
	}

	put<T extends TableName>(table: T, row: RowOf<T>): RowOf<T> {
		((this.changed[table] ??= new Map() as never) as Map<string, RowOf<T>>).set(keyOf(table, row), row);
		return row;
	}

	/** [row] with [change] applied and its edit stamped now. */
	touch<T extends TableName>(table: T, row: RowOf<T>, change: Partial<RowOf<T>> = {}): RowOf<T> {
		return this.put(table, { ...row, ...change, sync: { ...row.sync, updatedAt: this.now } });
	}

	remove<T extends TableName>(table: T, row: RowOf<T>): void {
		this.put(table, { ...row, sync: { ...row.sync, updatedAt: this.now, deletedAt: this.now } });
	}

	created(): { createdAt: number; updatedAt: number } {
		return { createdAt: this.now, updatedAt: this.now };
	}

	get today(): number {
		return epochDayOf(this.now);
	}

	changes(): Changes {
		const out: Changes = {};
		for (const [table, rows] of Object.entries(this.changed)) (out as Record<string, unknown[]>)[table] = [...rows.values()];
		return out;
	}
}

// ---- Read-throughs -------------------------------------------------------------------------

const byNumberDesc = (a: ReadThrough, b: ReadThrough) => b.number - a.number;

export function openReadThrough(tx: Tx, bookId: string): ReadThrough | undefined {
	return tx.all('readThroughs', (row) => row.bookId === bookId && row.finishedAt == null).sort(byNumberDesc)[0];
}

export function latestReadThrough(tx: Tx, bookId: string): ReadThrough | undefined {
	return tx.all('readThroughs', (row) => row.bookId === bookId).sort(byNumberDesc)[0];
}

function startReadThrough(tx: Tx, bookId: string): ReadThrough {
	const number = Math.max(0, ...tx.all('readThroughs', (row) => row.bookId === bookId).map((row) => row.number)) + 1;
	return tx.put('readThroughs', { id: newId(), bookId, number, startedAt: tx.now, sync: tx.created() });
}

function reopen(tx: Tx, readThrough: ReadThrough): ReadThrough {
	return tx.touch('readThroughs', readThrough, { finishedAt: undefined, finishedOn: undefined, outcome: undefined });
}

function progressionOf(locator: string | undefined): number | undefined {
	if (!locator) return undefined;
	try {
		const value = JSON.parse(locator)?.locations?.totalProgression;
		return typeof value === 'number' ? value : undefined;
	} catch {
		return undefined;
	}
}

// ---- Lifecycle -----------------------------------------------------------------------------

/**
 * Moves a book to [newState], keeping its read-throughs consistent: Reading and Paused have one
 * open, Completed and Dropped close it, and leaving a finished state takes that ending back. A
 * wishlist entry is always Planned.
 */
export function setState(tx: Tx, bookId: string, newState: ReadingState): void {
	const book = tx.get('books', bookId);
	if (!book || (book.state ?? 'PLANNED') === newState) return;
	if (book.acquisition === 'WISHLIST' && newState !== 'PLANNED') return;
	let progression = book.progression ?? 0;
	let physicalPage = book.physicalPage;

	const closed = FINISHED_STATES.includes(book.state ?? 'PLANNED') ? latestReadThrough(tx, bookId) : undefined;
	if (book.state === 'COMPLETED') progression = progressionOf(book.locator) ?? progression;

	if (newState === 'READING' || newState === 'PAUSED') {
		if (!openReadThrough(tx, bookId)) {
			if (closed) reopen(tx, closed);
			else startReadThrough(tx, bookId);
		}
	} else if (newState === 'COMPLETED' || newState === 'DROPPED') {
		const open = openReadThrough(tx, bookId) ?? closed ?? startReadThrough(tx, bookId);
		tx.touch('readThroughs', open, { finishedAt: tx.now, finishedOn: tx.today, outcome: newState });
		if (newState === 'COMPLETED') {
			progression = 1;
			if (!inLibrary(book) && physicalPage != null) physicalPage = book.physicalPageCount ?? book.pageCount ?? physicalPage;
		}
	} else {
		const open = openReadThrough(tx, bookId) ?? (closed ? reopen(tx, closed) : undefined);
		const unread =
			tx.all('readingSessions', (row) => row.bookId === bookId).length === 0 &&
			tx.all('physicalReadings', (row) => row.bookId === bookId).length === 0;
		if (open && unread) tx.remove('readThroughs', open);
	}

	const current = tx.get('books', bookId)!;
	tx.touch('books', current, {
		state: newState,
		progression,
		physicalPage,
		// The reading position changed, which only a later lastReadAt carries over to the other devices.
		lastReadAt: progression !== (current.progression ?? 0) ? tx.now : current.lastReadAt,
	});
}

/** Starts a new read-through from the beginning, leaving the earlier ones as they were. */
export function readAgain(tx: Tx, bookId: string): void {
	const book = tx.get('books', bookId);
	if (!book) return;
	const open = openReadThrough(tx, bookId);
	if (open) tx.touch('readThroughs', open, { finishedAt: tx.now, finishedOn: tx.today, outcome: 'DROPPED' });
	startReadThrough(tx, bookId);
	tx.touch('books', book, {
		state: 'READING',
		locator: undefined,
		progression: 0,
		position: undefined,
		chapterTitle: undefined,
		chapterIndex: undefined,
		physicalPage: book.physicalPage != null ? 0 : undefined,
		lastReadAt: tx.now,
	});
}

function currentReadThrough(tx: Tx, bookId: string, state: ReadingState): ReadThrough | undefined {
	return openReadThrough(tx, bookId) ?? (FINISHED_STATES.includes(state) ? latestReadThrough(tx, bookId) : undefined);
}

/**
 * The page reached in a physical copy. Moving it on records the pages in between as read today;
 * taking it back removes what this read-through recorded past it. A first page starts the book,
 * the last one completes it.
 */
export function setPhysicalProgress(tx: Tx, bookId: string, page: number | null, pageCount: number | null): void {
	const book = tx.get('books', bookId);
	if (!book || book.acquisition === 'WISHLIST') return;
	const count = pageCount != null && pageCount > 0 ? pageCount : undefined;
	const total = count ?? (book.pageCount && book.pageCount > 0 ? book.pageCount : undefined);
	const reached = page == null ? undefined : Math.min(Math.max(page, 0), total ?? Number.MAX_SAFE_INTEGER);
	const before = book.physicalPage ?? 0;
	tx.touch('books', book, {
		physicalPage: reached,
		physicalPageCount: count !== book.pageCount ? count : undefined,
		lastReadAt: reached != null && reached !== book.physicalPage ? tx.now : book.lastReadAt,
	});

	if ((reached ?? 0) < before) {
		const readThroughId = currentReadThrough(tx, bookId, book.state ?? 'PLANNED')?.id;
		const ofThis = (row: PhysicalReading) => row.bookId === bookId && (row.readThroughId ?? undefined) === readThroughId;
		for (const row of tx.all('physicalReadings', (row) => ofThis(row) && row.startPage >= (reached ?? 0)))
			tx.remove('physicalReadings', row);
		for (const row of tx.all('physicalReadings', (row) => ofThis(row) && row.endPage > (reached ?? 0))) {
			tx.touch('physicalReadings', row, { endPage: reached ?? 0 });
		}
	}
	if (reached == null || reached === 0 || reached === book.physicalPage) return;

	const state = book.state ?? 'PLANNED';
	const next: ReadingState =
		total != null && reached >= total ? 'COMPLETED' : state === 'PLANNED' || state === 'PAUSED' ? 'READING' : state;
	setState(tx, bookId, next);
	if (reached > before) {
		tx.put('physicalReadings', {
			id: newId(),
			bookId,
			readThroughId: currentReadThrough(tx, bookId, next)?.id,
			recordedAt: tx.now,
			day: tx.today,
			startPage: before,
			endPage: reached,
			sync: tx.created(),
		});
	}
}

// ---- The book itself ------------------------------------------------------------------------

export function setFavorite(tx: Tx, bookId: string, favorite: boolean): void {
	const book = tx.get('books', bookId);
	if (book) tx.touch('books', book, { isFavorite: favorite && book.acquisition !== 'WISHLIST' });
}

export function setRating(tx: Tx, bookId: string, rating: number | null): void {
	const book = tx.get('books', bookId);
	if (book) tx.touch('books', book, { rating: rating == null ? undefined : Math.min(Math.max(rating, 0.5), 5) });
}

export interface Purchase {
	acquisition: Acquisition;
	priceMinor: number | null;
	currency: string | null;
	purchasedOn: number | null;
	location: string | null;
	memo: string | null;
}

const tidy = (text: string | null | undefined): string | undefined => text?.trim() || undefined;

export function setPurchase(tx: Tx, bookId: string, purchase: Purchase): void {
	const book = tx.get('books', bookId);
	if (!book) return;
	const target: Acquisition = purchase.acquisition === 'PURCHASED' ? 'PURCHASED' : inLibrary(book) ? 'DOWNLOADED' : 'WISHLIST';
	if (target === 'WISHLIST') setState(tx, bookId, 'PLANNED');
	const current = tx.get('books', bookId)!;
	if (target !== 'PURCHASED') {
		tx.touch('books', current, {
			acquisition: target,
			isFavorite: !!current.isFavorite && target !== 'WISHLIST',
			purchasePriceMinor: undefined,
			purchaseCurrency: undefined,
			purchasedOn: undefined,
			purchaseLocation: undefined,
			purchaseMemo: undefined,
		});
	} else {
		tx.touch('books', current, {
			acquisition: target,
			purchasePriceMinor: purchase.priceMinor ?? undefined,
			purchaseCurrency: purchase.priceMinor != null ? (purchase.currency ?? undefined) : undefined,
			purchasedOn: purchase.purchasedOn ?? tx.today,
			purchaseLocation: tidy(purchase.location),
			purchaseMemo: tidy(purchase.memo),
		});
	}
}

/** A purchase with nothing on record of it: marked as bought today, or taken back. */
export const NO_PURCHASE = { priceMinor: null, currency: null, purchasedOn: null, location: null, memo: null } as const;

/** What [book] has on record of its purchase, to keep when it is marked purchased again. */
export function purchaseOf(book: Book): Purchase {
	return {
		acquisition: 'PURCHASED',
		priceMinor: book.purchasePriceMinor ?? null,
		currency: book.purchaseCurrency ?? null,
		purchasedOn: book.purchasedOn ?? null,
		location: book.purchaseLocation ?? null,
		memo: book.purchaseMemo ?? null,
	};
}

/** Saves a purchase typed in, and its currency as the one to offer next time, as the phone does. */
export function recordPurchase(tx: Tx, bookId: string, purchase: Purchase): void {
	setPurchase(tx, bookId, purchase);
	if (purchase.priceMinor != null && purchase.currency) setSetting(tx, 'lastCurrency', purchase.currency);
}

export interface Details {
	title: string;
	subtitle: string;
	authors: string[];
	description: string;
	publisher: string;
	series: string;
	seriesNumber: number | null;
	publishedDate: string;
	pageCount: number | null;
	isbn13: string;
}

/** Saves what the user typed about the book, which marks its details as theirs rather than the catalogue's. */
export function updateDetails(tx: Tx, bookId: string, details: Details): void {
	const book = tx.get('books', bookId);
	if (!book) return;
	const authors = details.authors.map((name) => name.trim()).filter(Boolean);
	const series = tidy(details.series);
	tx.touch('books', book, {
		title: details.title.trim() || book.title,
		subtitle: tidy(details.subtitle),
		authors,
		authorRefs: (book.authorRefs ?? []).filter((ref) => authors.includes(ref.name)),
		description: tidy(details.description),
		publisher: tidy(details.publisher),
		series,
		seriesNumber: series ? (details.seriesNumber ?? undefined) : undefined,
		publishedDate: tidy(details.publishedDate),
		pageCount: details.pageCount && details.pageCount > 0 ? details.pageCount : undefined,
		isbn13: tidy(details.isbn13),
		detailsEdited: true,
	});
}

export function setCover(tx: Tx, bookId: string, coverFile: string): void {
	const book = tx.get('books', bookId);
	if (book) tx.touch('books', book, { coverFile, customCover: true });
}

/** Drops the user's own cover, so the catalogue's shows again. */
export function removeCover(tx: Tx, bookId: string): void {
	const book = tx.get('books', bookId);
	if (book) tx.touch('books', book, { coverFile: undefined, customCover: false });
}

/** Removes the book and everything recorded of it. */
export function deleteBook(tx: Tx, bookId: string): void {
	const book = tx.get('books', bookId);
	if (!book) return;
	for (const table of ['readThroughs', 'readingSessions', 'physicalReadings', 'notes', 'shelfBooks'] as const) {
		for (const row of tx.all(table, (row) => row.bookId === bookId)) tx.remove(table, row);
	}
	tx.remove('books', book);
}

/** What the catalogue says about a book, for adding it. */
export interface Found {
	source: NonNullable<Book['source']>;
	sourceId: string;
	title: string;
	subtitle?: string;
	authors: string[];
	/** The catalogue's own ids for the authors, for their pages. */
	authorRefs?: { id: string; name: string }[];
	description?: string;
	publisher?: string;
	series?: string;
	seriesNumber?: number;
	publishedDate?: string;
	pageCount?: number;
	language?: string;
	isbn10?: string;
	isbn13?: string;
	categories?: string[];
	coverUrl?: string;
	/** The catalogue's average rating, out of 5. Shown, never stored. */
	averageRating?: number;
	infoUrl?: string;
}

/** Adds a book from the catalogue, or returns the one already added from the same record. */
export function addFound(tx: Tx, found: Found, acquisition: Acquisition): string {
	const existing = tx.all('books', (row) => row.source === found.source && row.sourceId === found.sourceId)[0];
	if (existing) return existing.id;
	const id = newId();
	tx.put('books', {
		id,
		title: found.title,
		subtitle: found.subtitle,
		authors: found.authors,
		authorRefs: found.authorRefs ?? [],
		description: found.description,
		publisher: found.publisher,
		series: found.series,
		seriesNumber: found.series ? found.seriesNumber : undefined,
		publishedDate: found.publishedDate,
		pageCount: found.pageCount,
		language: found.language,
		isbn10: found.isbn10,
		isbn13: found.isbn13,
		categories: found.categories ?? [],
		coverUrl: found.coverUrl,
		state: 'PLANNED',
		acquisition,
		purchasedOn: acquisition === 'PURCHASED' ? tx.today : undefined,
		source: found.source,
		sourceId: found.sourceId,
		sourceUrl: found.infoUrl,
		matchStatus: 'MATCHED',
		progression: 0,
		sync: tx.created(),
	});
	return id;
}

export function addManually(tx: Tx, details: Details, acquisition: Acquisition): string {
	const id = newId();
	tx.put('books', {
		id,
		title: details.title.trim(),
		authors: [],
		state: 'PLANNED',
		acquisition,
		purchasedOn: acquisition === 'PURCHASED' ? tx.today : undefined,
		matchStatus: 'NONE',
		progression: 0,
		sync: tx.created(),
	});
	updateDetails(tx, id, details);
	// A book typed in by hand starts as the user's own, which is what detailsEdited says of the source's.
	tx.touch('books', tx.get('books', id)!, { detailsEdited: false });
	return id;
}

// ---- Notes -----------------------------------------------------------------------------------

export function saveNote(tx: Tx, bookId: string, noteId: string | null, text: string): void {
	const existing = noteId ? tx.get('notes', noteId) : undefined;
	if (existing) tx.touch('notes', existing, { text: text.trim() });
	else tx.put('notes', { id: newId(), bookId, text: text.trim(), sync: tx.created() } satisfies Note);
}

export function deleteNote(tx: Tx, noteId: string): void {
	const note = tx.get('notes', noteId);
	if (note) tx.remove('notes', note);
}

// ---- Shelves ---------------------------------------------------------------------------------

export function createShelf(tx: Tx, name: string): string {
	const id = newId();
	tx.put('shelves', { id, name: name.trim(), sync: tx.created() } satisfies Shelf);
	return id;
}

export function renameShelf(tx: Tx, shelfId: string, name: string): void {
	const shelf = tx.get('shelves', shelfId);
	if (shelf) tx.touch('shelves', shelf, { name: name.trim() });
}

export function deleteShelf(tx: Tx, shelfId: string): void {
	const shelf = tx.get('shelves', shelfId);
	if (!shelf) return;
	for (const row of tx.all('shelfBooks', (row) => row.shelfId === shelfId)) tx.remove('shelfBooks', row);
	tx.remove('shelves', shelf);
}

/** Puts the book on the shelf, or takes it off. Putting it back writes the row over again, as on the phone. */
export function setOnShelf(tx: Tx, shelfId: string, bookId: string, on: boolean): void {
	const existing = tx.get('shelfBooks', `${shelfId}|${bookId}`);
	if (on) {
		if (existing && alive(existing)) return;
		tx.put('shelfBooks', {
			shelfId,
			bookId,
			sync: { createdAt: existing?.sync.createdAt ?? tx.now, updatedAt: tx.now },
		} satisfies ShelfBook);
	} else if (existing && alive(existing)) {
		tx.remove('shelfBooks', existing);
	}
}

/** Puts [bookIds] on the shelves [add] and on new ones named [create], and takes them off [remove]. */
export function setShelves(tx: Tx, bookIds: string[], add: string[], remove: string[], create: string[] = []): void {
	for (const shelfId of [...add, ...create.map((name) => createShelf(tx, name))])
		bookIds.forEach((id) => setOnShelf(tx, shelfId, id, true));
	for (const shelfId of remove) bookIds.forEach((id) => setOnShelf(tx, shelfId, id, false));
}

// ---- Goals and settings ----------------------------------------------------------------------

export function setYearlyGoal(tx: Tx, year: number, books: number): void {
	tx.put('yearlyGoals', { year, books: Math.max(1, Math.round(books)), sync: tx.created() } satisfies YearlyGoal);
}

export function setSetting(tx: Tx, key: string, value: unknown): void {
	const existing = tx.get('settings', key);
	tx.put('settings', { key, value, sync: { createdAt: existing?.sync.createdAt ?? tx.now, updatedAt: tx.now } } satisfies Setting);
}
