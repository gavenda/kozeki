import type { MetadataSource } from '@/types';
import { s } from '@/strings';
import {
	HARDCOVER_BROWSE_ORDERS,
	hardcoverAuthor,
	hardcoverBook,
	hardcoverBrowse,
	hardcoverReviews,
	hardcoverSearch,
	hardcoverSeries,
	hardcoverSignedIn,
	LookupError,
	type AuthorPage,
	type BookReviews,
	type FoundPage,
	type HardcoverBrowseOrder,
	type SeriesPage,
} from './hardcover';
import { ApiError, apiFetch } from './http';
import { toIsbn10, toIsbn13 } from './isbn';
import type { Found } from './library';

/**
 * The catalogues books are looked up in, as the phone's `MetadataProvider`s: Hardcover, which needs
 * the browser signed in to it and has authors, series, lists and reviews, and Google Books, which
 * needs no account of the user's and only searches. Either way a book is what `addFound` takes.
 */
export type BrowseOrder = HardcoverBrowseOrder;
export type { AuthorPage, BookReviews, FoundPage, SeriesPage };
export { LookupError };

export interface Provider {
	source: MetadataSource;
	search(query: string, page: number): Promise<FoundPage>;
	book(sourceId: string): Promise<Found | null>;
	browseOrders: BrowseOrder[];
	browse?(order: BrowseOrder, page: number): Promise<FoundPage>;
	author?(authorId: string, page: number): Promise<AuthorPage>;
	series?(name: string): Promise<SeriesPage>;
	reviews?(sourceId: string): Promise<BookReviews>;
}

const hardcover: Provider = {
	source: 'HARDCOVER',
	search: hardcoverSearch,
	book: hardcoverBook,
	browseOrders: HARDCOVER_BROWSE_ORDERS,
	browse: hardcoverBrowse,
	author: hardcoverAuthor,
	series: hardcoverSeries,
	reviews: hardcoverReviews,
};

const googleBooks: Provider = {
	source: 'GOOGLE_BOOKS',
	search: googleBooksSearch,
	book: googleBooksVolume,
	browseOrders: [],
};

export function providerFor(source: MetadataSource): Provider {
	return source === 'HARDCOVER' ? hardcover : googleBooks;
}

/** The catalogue to look in first: Hardcover when the browser is signed in to it, as the phone defaults to it. */
export function defaultSource(): MetadataSource {
	return hardcoverSignedIn() ? 'HARDCOVER' : 'GOOGLE_BOOKS';
}

/** What to tell the user about a failed lookup, in the phone's words. */
export function lookupMessage(error: unknown): string {
	if (error instanceof LookupError) return s.lookupError[error.reason];
	if (error instanceof ApiError) {
		if (error.status === 0) return s.lookupError.offline;
		if (error.status === 429) return s.lookupError.rateLimited;
		return error.message;
	}
	return s.lookupError.other;
}

// ---- Google Books ----------------------------------------------------------------------------

const GOOGLE_PAGE_SIZE = 20;

interface Volume {
	id: string;
	volumeInfo: {
		title?: string;
		subtitle?: string;
		authors?: string[];
		description?: string;
		publisher?: string;
		publishedDate?: string;
		pageCount?: number;
		language?: string;
		categories?: string[];
		averageRating?: number;
		industryIdentifiers?: { type: string; identifier: string }[];
		imageLinks?: { thumbnail?: string };
		infoLink?: string;
	};
}

function volumeToFound({ id, volumeInfo: info }: Volume): Found | null {
	if (!info.title) return null;
	const ids = info.industryIdentifiers ?? [];
	const isbn13 =
		toIsbn13(ids.find((entry) => entry.type === 'ISBN_13')?.identifier ?? '') ??
		toIsbn13(ids.find((entry) => entry.type === 'ISBN_10')?.identifier ?? '') ??
		undefined;
	return {
		source: 'GOOGLE_BOOKS',
		sourceId: id,
		title: info.title,
		subtitle: info.subtitle,
		authors: info.authors ?? [],
		// The description comes as markup.
		description:
			info.description
				?.replace(/<br\s*\/?>|<\/p>/gi, '\n')
				.replace(/<[^>]+>/g, '')
				.trim() || undefined,
		publisher: info.publisher,
		publishedDate: info.publishedDate,
		pageCount: info.pageCount || undefined,
		language: info.language,
		isbn10: isbn13 ? (toIsbn10(isbn13) ?? undefined) : undefined,
		isbn13,
		categories: info.categories,
		coverUrl: info.imageLinks?.thumbnail?.replace(/^http:/, 'https:'),
		averageRating: info.averageRating,
		infoUrl: info.infoLink,
	};
}

async function googleBooksSearch(query: string, page: number): Promise<FoundPage> {
	// Through the Worker, which adds the API key (see server/routes/books.ts).
	const start = (page - 1) * GOOGLE_PAGE_SIZE;
	const response = await apiFetch(`/books/search?q=${encodeURIComponent(query)}&start=${start}`);
	const body = (await response.json()) as { items?: Volume[]; totalItems?: number };
	const items = body.items ?? [];
	return {
		books: items.map(volumeToFound).filter((found): found is Found => found != null),
		hasMore: items.length >= GOOGLE_PAGE_SIZE && start + items.length < (body.totalItems ?? 0),
	};
}

async function googleBooksVolume(sourceId: string): Promise<Found | null> {
	try {
		const response = await apiFetch(`/books/volumes/${encodeURIComponent(sourceId)}`);
		return volumeToFound((await response.json()) as Volume);
	} catch (e) {
		if (e instanceof ApiError && e.status === 404) return null;
		throw e;
	}
}
