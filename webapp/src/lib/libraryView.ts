import type { Book, Shelf } from '@/types';
import { inLibrary } from './library';

/**
 * How the library is set out: its filters, orders and groups, ported from the phone's
 * `LibraryViewModel.kt` and `Shelves.kt`. Pure functions of the books, so the view only arranges
 * what these return. Keep them in step with the Kotlin.
 */

/**
 * The reading states sort the books by where the user is with them. A book only wanted is kept
 * apart under its own filter, so Planning is what is already there to be read. Purchased is every
 * book with a purchase on record. Favorites cut across all of that.
 */
export const LIBRARY_FILTERS = [
	'ALL',
	'FAVORITES',
	'READING',
	'PLANNED',
	'COMPLETED',
	'WISHLIST',
	'PURCHASED',
	'PAUSED',
	'DROPPED',
] as const;
export type LibraryFilter = (typeof LIBRARY_FILTERS)[number];

export const LIBRARY_SORTS = ['RECENT', 'TITLE', 'AUTHOR', 'DATE_ADDED', 'RATING', 'PRICE', 'SERIES'] as const;
export type LibrarySort = (typeof LIBRARY_SORTS)[number];

/** Each order's usual direction. */
const DESCENDING: Record<LibrarySort, boolean> = {
	RECENT: true,
	TITLE: false,
	AUTHOR: false,
	DATE_ADDED: true,
	RATING: true,
	PRICE: true,
	SERIES: false,
};

export type LibraryKind = 'BOOKS' | 'SHELVES';
export type BookDisplay = 'COVERS' | 'LIST';

const wished = (book: Book) => book.acquisition === 'WISHLIST';
const stateOf = (book: Book) => book.state ?? 'PLANNED';

export function filterMatches(filter: LibraryFilter, book: Book): boolean {
	switch (filter) {
		case 'ALL':
			return true;
		case 'FAVORITES':
			return !!book.isFavorite;
		case 'WISHLIST':
			return wished(book);
		case 'PURCHASED':
			return book.acquisition === 'PURCHASED';
		default:
			return !wished(book) && stateOf(book) === filter;
	}
}

export function filterCounts(books: Book[]): Record<LibraryFilter, number> {
	return Object.fromEntries(
		LIBRARY_FILTERS.map((filter) => [filter, books.filter((book) => filterMatches(filter, book)).length]),
	) as Record<LibraryFilter, number>;
}

const addedAt = (book: Book) => book.sync.createdAt;

/**
 * The order the books come in before any sort, which is [RECENT]'s: books with an EPUB first, most
 * recently read at the top, then the ones still without a file, newest first.
 */
export function recentOrder(books: Book[]): Book[] {
	const withFile = books.filter(inLibrary).sort((a, b) => (b.lastReadAt ?? addedAt(b)) - (a.lastReadAt ?? addedAt(a)));
	const without = books.filter((book) => !inLibrary(book)).sort((a, b) => addedAt(b) - addedAt(a));
	return [...withFile, ...without];
}

export function isDescending(sort: LibrarySort, reversed: boolean): boolean {
	return DESCENDING[sort] !== reversed;
}

/** Whether [sort] has anything to go by for [book]; the ones it has not go at the end either way. */
function hasValue(sort: LibrarySort, book: Book): boolean {
	switch (sort) {
		case 'RECENT':
			return inLibrary(book) || book.lastReadAt != null;
		case 'AUTHOR':
			return (book.authors ?? []).length > 0;
		case 'RATING':
			return book.rating != null;
		case 'PRICE':
			return book.purchasePriceMinor != null;
		case 'SERIES':
			return book.series != null;
		default:
			return true;
	}
}

const collator = new Intl.Collator(undefined, { sensitivity: 'base' });
type Compare = (a: Book, b: Book) => number;
const flip = (compare: Compare, reversed: boolean): Compare => (reversed ? (a, b) => compare(b, a) : compare);
const then =
	(...compares: Compare[]): Compare =>
	(a, b) => {
		for (const compare of compares) {
			const result = compare(a, b);
			if (result !== 0) return result;
		}
		return 0;
	};

/** [books], given in [recentOrder], in [sort], or the other way round when [reversed]. Ties keep the recent order. */
export function sortBooks(sort: LibrarySort, books: Book[], reversed = false): Book[] {
	if (sort === 'RECENT' && !reversed) return books;
	const placed = books.filter((book) => hasValue(sort, book));
	const unplaced = books.filter((book) => !hasValue(sort, book));
	let ordered: Book[];
	switch (sort) {
		case 'RECENT':
			ordered = [...placed].reverse();
			break;
		case 'TITLE':
			ordered = [...placed].sort(flip((a, b) => collator.compare(a.title, b.title), reversed));
			break;
		case 'AUTHOR':
			ordered = [...placed].sort(flip((a, b) => collator.compare(a.authors![0], b.authors![0]), reversed));
			break;
		case 'DATE_ADDED':
			ordered = [...placed].sort(flip((a, b) => addedAt(b) - addedAt(a), reversed));
			break;
		case 'RATING':
			ordered = [...placed].sort(flip((a, b) => b.rating! - a.rating!, reversed));
			break;
		case 'PRICE': {
			// Prices in different currencies cannot be weighed against each other, so each currency is
			// kept together, the one most books were bought in first, whichever way the prices run.
			const counts = new Map<string, number>();
			for (const book of placed) counts.set(book.purchaseCurrency ?? '', (counts.get(book.purchaseCurrency ?? '') ?? 0) + 1);
			ordered = [...placed].sort(
				then(
					(a, b) => counts.get(b.purchaseCurrency ?? '')! - counts.get(a.purchaseCurrency ?? '')!,
					(a, b) => (a.purchaseCurrency ?? '').localeCompare(b.purchaseCurrency ?? ''),
					flip((a, b) => b.purchasePriceMinor! - a.purchasePriceMinor!, reversed),
				),
			);
			break;
		}
		case 'SERIES':
			ordered = [...placed].sort(
				then(
					flip((a, b) => collator.compare(a.series ?? '', b.series ?? ''), reversed),
					(a, b) => Number(a.seriesNumber == null) - Number(b.seriesNumber == null),
					flip((a, b) => (a.seriesNumber ?? 0) - (b.seriesNumber ?? 0), reversed),
					(a, b) => collator.compare(a.title, b.title),
				),
			);
			break;
	}
	return [...ordered, ...unplaced];
}

/** A run of books in the grid. [heading] is the filter they would be found under; null for no heading. */
export interface LibrarySection {
	heading: LibraryFilter | null;
	books: Book[];
}

/** Between them these take in every book exactly once. The wishlist follows what is planned to be read. */
const GROUP_FILTERS: LibraryFilter[] = ['READING', 'PLANNED', 'WISHLIST', 'COMPLETED', 'PAUSED', 'DROPPED'];

/**
 * How [books], already filtered and sorted, are laid out: every book at once is grouped by reading
 * state, the ones being read first and the ones only wanted in a group of their own, while any
 * narrower filter is a single run.
 */
export function librarySections(filter: LibraryFilter, books: Book[]): LibrarySection[] {
	if (books.length === 0) return [];
	if (filter !== 'ALL') return [{ heading: null, books }];
	return GROUP_FILTERS.flatMap((group) => {
		const run = books.filter((book) => filterMatches(group, book));
		return run.length ? [{ heading: group, books: run }] : [];
	});
}

export interface BookGroup {
	/** What the group is kept as among the folded ones. */
	key: string;
	sections: LibrarySection[];
	books: Book[];
}

export interface SeriesGroup extends BookGroup {
	name: string | null;
}

/** [books], already in [SERIES] order, one series after another, each laid out in the library's own groups. */
export function seriesGroups(filter: LibraryFilter, books: Book[]): SeriesGroup[] {
	const runs = new Map<string, Book[]>();
	for (const book of books) {
		const key = book.series?.toLowerCase() ?? '';
		(runs.get(key) ?? runs.set(key, []).get(key)!).push(book);
	}
	return [...runs.values()].map((run) => ({
		key: `series/BOOKS/${run[0].series?.toLowerCase() ?? ''}`,
		name: run[0].series ?? null,
		sections: librarySections(filter, run),
		books: run,
	}));
}

export interface ShelfGroup extends BookGroup {
	shelf: Shelf;
}

/**
 * The shelves, each with its books from [books] (already filtered and sorted). A book on two
 * shelves is under both. A shelf the filter leaves nothing of is left out, except that every shelf
 * shows under All, empty or not.
 */
export function shelfGroups(shelves: Shelf[], members: Map<string, Set<string>>, books: Book[], filter: LibraryFilter): ShelfGroup[] {
	return shelves.flatMap((shelf) => {
		const onShelf = members.get(shelf.id) ?? new Set<string>();
		const run = books.filter((book) => onShelf.has(book.id));
		const sections = librarySections(filter, run);
		if (sections.length === 0 && filter !== 'ALL') return [];
		return [{ key: `shelf/${shelf.id}`, shelf, sections, books: run }];
	});
}

/** What [books] cost, one sum for each currency, the one most of them were bought in first. */
export function priceTotals(books: Book[]): { currency: string | undefined; minor: number }[] {
	const byCurrency = new Map<string | undefined, Book[]>();
	for (const book of books.filter((book) => book.purchasePriceMinor != null)) {
		(byCurrency.get(book.purchaseCurrency) ?? byCurrency.set(book.purchaseCurrency, []).get(book.purchaseCurrency)!).push(book);
	}
	return [...byCurrency]
		.sort((a, b) => b[1].length - a[1].length)
		.map(([currency, priced]) => ({ currency, minor: priced.reduce((sum, book) => sum + book.purchasePriceMinor!, 0) }));
}

export function averageRating(books: Book[]): number | null {
	const rated = books.flatMap((book) => (book.rating != null ? [book.rating] : []));
	return rated.length ? rated.reduce((a, b) => a + b, 0) / rated.length : null;
}

/** As the phone's `BookMatcher.normalize`: no accents, no case, punctuation as spaces. */
export function normalize(text: string): string {
	return text
		.normalize('NFD')
		.replace(/\p{Mn}+/gu, '')
		.toLowerCase()
		.replace(/[^\p{L}\p{N}]+/gu, ' ')
		.trim();
}

// "Ender's Game" should also be found by "enders game", so an apostrophe joins instead of splitting.
const searchable = (text: string) => normalize(text.replace(/['’ʼ]/g, ''));

export interface SearchGroups {
	library: Book[];
	wishlist: Book[];
	purchased: Book[];
}

/**
 * The books in which every word of [query] appears, in the title, the subtitle or an author's
 * name, grouped by where each lives: the library (an EPUB), the wishlist, or the purchased list.
 * An empty query finds every book. Books keep the order they were given in.
 */
export function searchBooks(query: string, books: Book[]): SearchGroups {
	const words = searchable(query).split(' ').filter(Boolean);
	const found = !query.trim()
		? books
		: words.length === 0
			? []
			: books.filter((book) => {
					const text = searchable([book.title, book.subtitle, ...(book.authors ?? [])].filter(Boolean).join(' '));
					return words.every((word) => text.includes(word));
				});
	return {
		library: found.filter(inLibrary),
		wishlist: found.filter((book) => !inLibrary(book) && wished(book)),
		purchased: found.filter((book) => !inLibrary(book) && !wished(book)),
	};
}
