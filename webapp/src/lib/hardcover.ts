import { auth0, HARDCOVER_CALLBACK } from './auth0';
import { apiFetch } from './http';
import { toIsbn10, toIsbn13 } from './isbn';
import type { Found } from './library';
import { normalize } from './libraryView';

/**
 * The browser's own Hardcover sign-in: the web app's OAuth client (authorization code with PKCE, no
 * secret), with the token kept in this browser. Hardcover's endpoints do not answer other origins,
 * so the token exchange and the API go through the Worker (`/api/hardcover/*`), which stores nothing.
 */
const CLIENT_ID = import.meta.env.VITE_HARDCOVER_CLIENT_ID;
const AUTHORIZE = 'https://hardcover.app/oauth2/authorize';
const ISSUER = 'https://api.hardcover.app';
// The same scopes the phone asks for (app/build.gradle.kts): read:catalog covers search, reviews by
// other readers need read:social and read:library:public, read:users adds who wrote each one, and
// read:me:content and read:me:email only add the account. Ones the client does not allow are dropped.
const SCOPES = 'read:catalog read:social read:library:public read:users read:me:content read:me:email';
const STORE = 'kozeki.hardcover';
const PENDING = 'kozeki.hardcover.pending';

interface Session {
	accessToken: string;
	refreshToken?: string;
	expiresAt: number;
}

export const hardcoverAvailable = (): boolean => Boolean(CLIENT_ID);

const redirectUri = () => `${window.location.origin}${HARDCOVER_CALLBACK}`;

function read<T>(key: string, storage: Storage = localStorage): T | null {
	try {
		const raw = storage.getItem(key);
		return raw ? (JSON.parse(raw) as T) : null;
	} catch {
		return null;
	}
}

/** Writes [value] to [key], or removes it when null. Storage can refuse (a private window, a full quota); then nothing is kept. */
function write(key: string, value: unknown, storage: Storage = localStorage): boolean {
	try {
		if (value == null) storage.removeItem(key);
		else storage.setItem(key, JSON.stringify(value));
		return true;
	} catch {
		return false;
	}
}

export const hardcoverSignedIn = (): boolean => hardcoverAvailable() && read<Session>(STORE) != null;

function randomUrlSafe(bytes: number): string {
	const data = crypto.getRandomValues(new Uint8Array(bytes));
	return btoa(String.fromCharCode(...data))
		.replace(/\+/g, '-')
		.replace(/\//g, '_')
		.replace(/=+$/, '');
}

async function challengeFor(verifier: string): Promise<string> {
	const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
	return btoa(String.fromCharCode(...digest))
		.replace(/\+/g, '-')
		.replace(/\//g, '_')
		.replace(/=+$/, '');
}

/** Sends the browser to Hardcover to approve the sign-in. It comes back to `/hardcover`. */
export async function startHardcoverSignIn(): Promise<void> {
	const state = randomUrlSafe(24);
	const verifier = randomUrlSafe(32);
	if (!write(PENDING, { state, verifier }, sessionStorage)) return;
	const url = new URL(AUTHORIZE);
	url.search = new URLSearchParams({
		response_type: 'code',
		client_id: CLIENT_ID!,
		redirect_uri: redirectUri(),
		state,
		code_challenge: await challengeFor(verifier),
		code_challenge_method: 'S256',
		scope: SCOPES,
	}).toString();
	window.location.assign(url.toString());
}

async function requestTokens(form: Record<string, string>): Promise<Session | null> {
	const response = await apiFetch('/hardcover/token', {
		method: 'POST',
		headers: { 'content-type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams(form).toString(),
	}).catch(() => null);
	if (!response) return null;
	const body = (await response.json()) as { access_token?: string; refresh_token?: string; expires_in?: number };
	if (!body.access_token) return null;
	const session = {
		accessToken: body.access_token,
		refreshToken: body.refresh_token,
		expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
	};
	return write(STORE, session) ? session : null;
}

/** Finishes the sign-in Hardcover sent the browser back from. Returns false when it was refused or cannot be trusted. */
export async function completeHardcoverSignIn(params: URLSearchParams): Promise<boolean> {
	const pending = read<{ state: string; verifier: string }>(PENDING, sessionStorage);
	write(PENDING, null, sessionStorage);
	const code = params.get('code');
	if (!pending || !code || params.get('state') !== pending.state) return false;
	// A code from some other server must not be redeemed here (RFC 9207).
	if (params.has('iss') && params.get('iss') !== ISSUER) return false;
	const session = await requestTokens({
		grant_type: 'authorization_code',
		code,
		redirect_uri: redirectUri(),
		code_verifier: pending.verifier,
		client_id: CLIENT_ID!,
	});
	return session != null;
}

// Refresh tokens are single-use, so two refreshes must not race.
let refreshing: Promise<Session | null> | null = null;

async function accessToken(): Promise<string | null> {
	const session = read<Session>(STORE);
	if (!session) return null;
	if (Date.now() < session.expiresAt - 60_000) return session.accessToken;
	if (!session.refreshToken) return null;
	refreshing ??= requestTokens({ grant_type: 'refresh_token', refresh_token: session.refreshToken, client_id: CLIENT_ID! }).finally(() => {
		refreshing = null;
	});
	const fresh = await refreshing;
	if (!fresh) write(STORE, null);
	return fresh?.accessToken ?? null;
}

export function hardcoverSignOut(): void {
	const session = read<Session>(STORE);
	write(STORE, null);
	if (session?.refreshToken) {
		void apiFetch('/hardcover/revoke', {
			method: 'POST',
			headers: { 'content-type': 'application/x-www-form-urlencoded' },
			body: new URLSearchParams({ token: session.refreshToken, token_type_hint: 'refresh_token', client_id: CLIENT_ID! }).toString(),
		}).catch(() => undefined);
	}
}

// ---- The API ---------------------------------------------------------------------------------

/** Why a lookup failed, as the phone tells them apart. */
export class LookupError extends Error {
	readonly reason: 'signedOut' | 'rateLimited' | 'unauthorized' | 'missingPermission' | 'offline' | 'other';
	readonly scope?: string;

	constructor(reason: LookupError['reason'], message: string = reason, scope?: string) {
		super(message);
		this.name = 'LookupError';
		this.reason = reason;
		this.scope = scope;
	}
}

type Json = Record<string, unknown>;

/** Sends a GraphQL request through the Worker. */
async function gql(query: string, variables: Json): Promise<Json> {
	const token = await accessToken();
	if (!token) throw new LookupError('signedOut');
	let response: Response;
	try {
		response = await fetch('/api/hardcover/graphql', {
			method: 'POST',
			headers: {
				'content-type': 'application/json',
				'X-Hardcover-Authorization': `Bearer ${token}`,
				...(await kozekiAuthorization()),
			},
			body: JSON.stringify({ query, variables }),
		});
	} catch {
		throw new LookupError('offline');
	}
	const text = await response.text();
	const body = (() => {
		try {
			return JSON.parse(text) as Json;
		} catch {
			return null;
		}
	})();
	if (response.status === 429) throw new LookupError('rateLimited');
	if (response.status === 403 && body?.error === 'insufficient_scope')
		throw new LookupError('missingPermission', text, String(body.scope ?? ''));
	if (response.status === 401 || response.status === 403) throw new LookupError('unauthorized');
	if (!response.ok || !body) throw new LookupError('other', `Hardcover answered ${response.status}`);
	const errors = body.errors as { message?: string }[] | undefined;
	if (errors?.length) throw new LookupError('other', errors[0].message ?? 'GraphQL error');
	return (body.data as Json) ?? {};
}

/** The Kozeki account's token, which the Worker's route needs before it forwards anything. */
async function kozekiAuthorization(): Promise<Record<string, string>> {
	try {
		const token = await auth0.getAccessTokenSilently();
		return token ? { authorization: `Bearer ${token}` } : {};
	} catch {
		return {};
	}
}

const obj = (value: unknown): Json | undefined =>
	value && typeof value === 'object' && !Array.isArray(value) ? (value as Json) : undefined;
const arr = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const str = (value: unknown): string | undefined =>
	typeof value === 'string' ? value : typeof value === 'number' ? String(value) : undefined;
const num = (value: unknown): number | undefined => {
	const n = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
	return Number.isFinite(n) ? n : undefined;
};
/** Text on one line, or nothing when there is none. */
const line = (value: unknown): string | undefined => str(value)?.replace(/\s+/g, ' ').trim() || undefined;
const https = (value: unknown): string | undefined => {
	const url = str(value);
	return url?.startsWith('https://') ? url : undefined;
};

// Deliberately narrow: a "collection" on its own is as often one book of short stories.
const COLLECTION_TITLE =
	/\b(box(ed)?[ -]?set|omnibus|(e?books?|series|trilogy) bundle|\d+[ -]book\b|books? \d+ ?(-|–|—|to|&|and) ?\d+|(complete|entire|whole) (series|saga|trilogy|collection)|(series|trilogy|saga) collection|collection set|\d+ (books|volumes|novels) (collection|set)|vol(ume)?s?\.? ?\d+ ?[-–—] ?\d+)/i;

/** Several books sold as one: a box set, an omnibus, a bundle. */
const isCollection = (doc: Json) =>
	doc.compilation === true || [str(doc.title), str(doc.subtitle)].some((t) => t != null && COLLECTION_TITLE.test(t));

const WRITER_ROLES = new Set(['author', 'writer']);
/** Whether a contribution is the writing of the book. The main author's has no role at all. */
const isWriter = (role: unknown) => !str(role)?.trim() || WRITER_ROLES.has(str(role)!.trim().toLowerCase());

function authorRef(author: unknown): { id: string; name: string } | undefined {
	const id = str(obj(author)?.id);
	const name = line(obj(author)?.name);
	return id && name ? { id, name } : undefined;
}

const distinctRefs = (refs: { id: string; name: string }[]) =>
	refs.filter((ref, index) => refs.findIndex((r) => r.id === ref.id) === index);

/** A search document, which names things differently from a row of the books table. */
function documentToFound(doc: Json, queriedIsbn?: string): Found | null {
	const id = str(doc.id);
	const title = line(doc.title);
	if (!id || !title) return null;
	const isbns = arr(doc.isbns).flatMap((isbn) => (typeof isbn === 'string' ? [toIsbn13(isbn)].filter((i): i is string => !!i) : []));
	// A book's search document lists the ISBNs of all its editions; keep the one that was asked for.
	const isbn13 = queriedIsbn && isbns.includes(queriedIsbn) ? queriedIsbn : isbns[0];
	const series = obj(doc.featured_series);
	const seriesName = line(obj(series?.series)?.name);
	const refs = distinctRefs(
		arr(doc.contributions).flatMap((c) => [authorRef(obj(c)?.author)].filter((r) => !!r)) as { id: string; name: string }[],
	);
	return {
		source: 'HARDCOVER',
		sourceId: id,
		title,
		subtitle: line(doc.subtitle),
		authors: arr(doc.author_names).flatMap((name) => [line(name)].filter((n): n is string => !!n)),
		authorRefs: refs,
		description: str(doc.description)?.trim() || undefined,
		series: seriesName,
		seriesNumber: seriesName ? (num(series?.position) ?? num(doc.featured_series_position)) : undefined,
		publishedDate: str(doc.release_date) ?? str(doc.release_year),
		pageCount: num(doc.pages) || undefined,
		isbn10: isbn13 ? (toIsbn10(isbn13) ?? undefined) : undefined,
		isbn13,
		categories: arr(doc.genres).flatMap((genre) => [line(genre)].filter((g): g is string => !!g)),
		coverUrl: https(obj(doc.image)?.url),
		averageRating: (num(doc.rating) ?? 0) > 0 ? num(doc.rating) : undefined,
		infoUrl: str(doc.slug) ? `https://hardcover.app/books/${str(doc.slug)}` : undefined,
	};
}

/** A row of the books table. */
function bookToFound(book: Json): Found | null {
	const id = str(book.id);
	const title = line(book.title);
	if (!id || !title) return null;
	const contributors = distinctRefs(
		arr(book.contributions).flatMap((c) => (isWriter(obj(c)?.contribution) ? [authorRef(obj(c)?.author)].filter((r) => !!r) : [])) as {
			id: string;
			name: string;
		}[],
	);
	const isbn13 = ['default_physical_edition', 'default_ebook_edition']
		.map((edition) => str(obj(book[edition])?.isbn_13))
		.map((isbn) => (isbn ? toIsbn13(isbn) : null))
		.find((isbn): isbn is string => !!isbn);
	const series = obj(book.featured_book_series);
	const seriesName = line(obj(series?.series)?.name);
	return {
		source: 'HARDCOVER',
		sourceId: id,
		title,
		subtitle: line(book.subtitle),
		authors: contributors.map((ref) => ref.name),
		authorRefs: contributors,
		description: str(book.description)?.trim() || undefined,
		series: seriesName,
		seriesNumber: seriesName ? num(series?.position) : undefined,
		publishedDate: str(book.release_date) ?? str(book.release_year),
		pageCount: num(book.pages) || undefined,
		isbn10: isbn13 ? (toIsbn10(isbn13) ?? undefined) : undefined,
		isbn13,
		coverUrl: https(obj(book.image)?.url),
		averageRating: (num(book.rating) ?? 0) > 0 ? num(book.rating) : undefined,
		infoUrl: str(book.slug) ? `https://hardcover.app/books/${str(book.slug)}` : undefined,
	};
}

const found = (rows: (Found | null)[]): Found[] => rows.filter((row): row is Found => row != null);

export interface FoundPage {
	books: Found[];
	hasMore: boolean;
}

// ---- Queries, copied from the phone's HardcoverProvider.kt ------------------------------------

const SEARCH_PAGE_SIZE = 12;
const SEARCH_QUERY =
	'query Search($query: String!, $page: Int!, $perPage: Int!) { search(query: $query, query_type: "Book", per_page: $perPage, page: $page) { results } }';

const AUTHOR_PAGE_SIZE = 20;
const AUTHOR_BOOK_ROWS =
	'contributions(where: { contributable_type: { _eq: "Book" }, book: { canonical_id: { _is_null: true } } }, ' +
	'order_by: [{ book: { users_count: desc } }, { id: asc }], limit: $limit, offset: $offset)';
const AUTHOR_BOOK_FIELDS =
	'id title subtitle slug description release_date release_year pages rating compilation image { url } ' +
	'contributions { contribution author { id name } } ' +
	'default_physical_edition { isbn_13 } default_ebook_edition { isbn_13 } ' +
	'featured_book_series { position series { name } }';
const AUTHOR_QUERY =
	'query Author($id: Int!, $limit: Int!, $offset: Int!) { ' +
	'authors_by_pk(id: $id) { name bio born_year death_year location books_count slug image { url } ' +
	`${AUTHOR_BOOK_ROWS} { contribution book { ${AUTHOR_BOOK_FIELDS} } } } }`;

const SERIES_BOOKS_LIMIT = 100;
const SERIES_FIELDS =
	'id name slug book_series(distinct_on: position, ' +
	'order_by: [{ position: asc }, { book: { users_count: desc } }], ' +
	'where: { compilation: { _eq: false }, book: { canonical_id: { _is_null: true }, is_partial_book: { _eq: false } } }, ' +
	`limit: ${SERIES_BOOKS_LIMIT}) { position book { ${AUTHOR_BOOK_FIELDS} } }`;
const SERIES_BY_NAME_QUERY =
	'query Series($name: String!) { ' +
	'series(where: { name: { _eq: $name }, canonical_id: { _is_null: true }, books_count: { _gt: 0 } }, ' +
	`order_by: { books_count: desc }, limit: 1) { ${SERIES_FIELDS} } }`;
const SERIES_BY_ID_QUERY = `query Series($id: Int!) { series(where: { id: { _eq: $id } }, limit: 1) { ${SERIES_FIELDS} } }`;
const SERIES_SEARCH_QUERY =
	'query SeriesSearch($query: String!) { search(query: $query, query_type: "Series", per_page: 1, page: 1) { results } }';

const BROWSE_PAGE_SIZE = 24;
const TRENDING_QUERY =
	'query Trending($duration: TrendingDuration!, $limit: Int!, $offset: Int!) { books_trending(duration: $duration, limit: $limit, offset: $offset) { ids } }';
const BOOKS_BY_IDS_QUERY = `query Books($ids: [Int!]!) { books(where: { id: { _in: $ids } }) { ${AUTHOR_BOOK_FIELDS} } }`;
const WHOLE_BOOKS = 'canonical_id: { _is_null: true }, compilation: { _eq: false }, is_partial_book: { _eq: false }';
const TOP_RATED_QUERY =
	'query TopRated($limit: Int!, $offset: Int!) { ' +
	`books(where: { ${WHOLE_BOOKS}, ratings_count: { _gte: 1000 } }, ` +
	`order_by: [{ rating: desc_nulls_last }, { id: asc }], limit: $limit, offset: $offset) { ${AUTHOR_BOOK_FIELDS} } }`;
const NEW_RELEASES_QUERY =
	'query NewReleases($today: date!, $limit: Int!, $offset: Int!) { ' +
	`books(where: { ${WHOLE_BOOKS}, release_date: { _lte: $today }, users_count: { _gte: 50 } }, ` +
	`order_by: [{ release_date: desc }, { users_count: desc }], limit: $limit, offset: $offset) { ${AUTHOR_BOOK_FIELDS} } }`;

const BOOK_QUERY = `query Book($id: Int!) { books_by_pk(id: $id) { ${AUTHOR_BOOK_FIELDS} } }`;

const REVIEWS_LIMIT = 20;
const USERS_SCOPE = 'read:users';
const REVIEW_ROWS =
	'user_books(where: { book_id: { _eq: $id }, has_review: { _eq: true } }, ' +
	'order_by: [{ likes_count: desc }, { reviewed_at: desc_nulls_last }], limit: $limit)';
const REVIEW_FIELDS = 'id rating review_raw review review_has_spoilers reviewed_at likes_count';
const BOOK_FIGURES = 'books_by_pk(id: $id) { rating ratings_count reviews_count }';
const REVIEWS_QUERY = `query Reviews($id: Int!, $limit: Int!) { ${BOOK_FIGURES} ${REVIEW_ROWS} { ${REVIEW_FIELDS} user { name username } } }`;
const ANONYMOUS_REVIEWS_QUERY = `query Reviews($id: Int!, $limit: Int!) { ${BOOK_FIGURES} ${REVIEW_ROWS} { ${REVIEW_FIELDS} } }`;

// ---- Lookups ---------------------------------------------------------------------------------

async function searchDocuments(query: string, page: number): Promise<{ documents: Json[]; hasMore: boolean }> {
	const data = await gql(SEARCH_QUERY, { query, page, perPage: SEARCH_PAGE_SIZE });
	const results = obj(obj(data.search)?.results);
	const hits = arr(results?.hits);
	// The total is the surer sign of a next page; without it, a full page suggests one.
	const total = num(results?.found);
	return {
		documents: hits.flatMap((hit) => [obj(obj(hit)?.document)].filter((d): d is Json => !!d)),
		hasMore: total != null ? page * SEARCH_PAGE_SIZE < total : hits.length >= SEARCH_PAGE_SIZE,
	};
}

/** A search is for one book to read, so sets of several are left out. */
export async function hardcoverSearch(query: string, page = 1): Promise<FoundPage> {
	const hits = await searchDocuments(query, page);
	return { books: found(hits.documents.filter((doc) => !isCollection(doc)).map((doc) => documentToFound(doc))), hasMore: hits.hasMore };
}

export type HardcoverBrowseOrder = 'TRENDING_MONTH' | 'TRENDING_WEEK' | 'TRENDING_YEAR' | 'POPULAR' | 'TOP_RATED' | 'NEW_RELEASES';
export const HARDCOVER_BROWSE_ORDERS: HardcoverBrowseOrder[] = [
	'TRENDING_MONTH',
	'TRENDING_WEEK',
	'TRENDING_YEAR',
	'POPULAR',
	'TOP_RATED',
	'NEW_RELEASES',
];

export async function hardcoverBrowse(order: HardcoverBrowseOrder, page: number): Promise<FoundPage> {
	const offset = (page - 1) * BROWSE_PAGE_SIZE;
	const duration = { TRENDING_WEEK: 'week', TRENDING_MONTH: 'month', TRENDING_YEAR: 'one_year', POPULAR: 'all' }[order as string];
	if (duration) {
		// Trending only names the books, so their details take a second request, in no order of their own.
		const ids = arr(obj((await gql(TRENDING_QUERY, { duration, limit: BROWSE_PAGE_SIZE, offset })).books_trending)?.ids).flatMap((id) =>
			typeof id === 'number' ? [id] : [],
		);
		if (ids.length === 0) return { books: [], hasMore: false };
		const rows = arr((await gql(BOOKS_BY_IDS_QUERY, { ids })).books).flatMap((row) => [obj(row)].filter((r): r is Json => !!r));
		const byId = new Map(rows.map((row) => [num(row.id), row]));
		return {
			books: found(
				ids
					.map((id) => byId.get(id))
					.filter((row): row is Json => !!row && !isCollection(row))
					.map(bookToFound),
			),
			hasMore: ids.length >= BROWSE_PAGE_SIZE,
		};
	}
	// The local date, as the phone's LocalDate.now().
	const now = new Date();
	const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
	const data =
		order === 'TOP_RATED'
			? await gql(TOP_RATED_QUERY, { limit: BROWSE_PAGE_SIZE, offset })
			: await gql(NEW_RELEASES_QUERY, { limit: BROWSE_PAGE_SIZE, offset, today });
	const rows = arr(data.books).flatMap((row) => [obj(row)].filter((r): r is Json => !!r));
	return { books: found(rows.filter((row) => !isCollection(row)).map(bookToFound)), hasMore: rows.length >= BROWSE_PAGE_SIZE };
}

export async function hardcoverBook(sourceId: string): Promise<Found | null> {
	// Hardcover's book IDs are integers; anything else cannot be one of its books.
	const id = Number(sourceId);
	if (!Number.isInteger(id)) return null;
	const book = obj((await gql(BOOK_QUERY, { id })).books_by_pk);
	return book ? bookToFound(book) : null;
}

export interface Author {
	id: string;
	name: string;
	bio?: string;
	bornYear?: number;
	deathYear?: number;
	location?: string;
	booksCount: number;
	imageUrl?: string;
	infoUrl?: string;
}

export interface AuthorPage {
	author: Author | null;
	books: FoundPage;
}

const plainText = (markup: string) =>
	markup
		.replace(/<br\s*\/?>|<\/p>/gi, '\n')
		.replace(/<[^>]+>/g, '')
		.trim();

export async function hardcoverAuthor(authorId: string, page: number): Promise<AuthorPage> {
	const id = Number(authorId);
	const empty = { author: null, books: { books: [], hasMore: false } };
	if (!Number.isInteger(id)) return empty;
	const author = obj((await gql(AUTHOR_QUERY, { id, limit: AUTHOR_PAGE_SIZE, offset: (page - 1) * AUTHOR_PAGE_SIZE })).authors_by_pk);
	const name = line(author?.name);
	if (!author || !name) return empty;
	const rows = arr(author.contributions).flatMap((row) => [obj(row)].filter((r): r is Json => !!r));
	return {
		author: {
			id: authorId,
			name,
			bio: str(author.bio) ? plainText(str(author.bio)!) || undefined : undefined,
			bornYear: num(author.born_year),
			deathYear: num(author.death_year),
			location: line(author.location),
			booksCount: num(author.books_count) ?? 0,
			imageUrl: https(obj(author.image)?.url),
			infoUrl: str(author.slug) ? `https://hardcover.app/authors/${str(author.slug)}` : undefined,
		},
		books: {
			// Their own books only, not the ones they translated, narrated or introduced.
			books: found(
				rows
					.filter((row) => isWriter(row.contribution))
					.map((row) => obj(row.book))
					.filter((book): book is Json => !!book && !isCollection(book))
					.map(bookToFound),
			),
			hasMore: rows.length >= AUTHOR_PAGE_SIZE,
		},
	};
}

export interface SeriesPage {
	name: string | null;
	infoUrl?: string;
	books: Found[];
}

/**
 * The series called exactly [name], with the most books of several; failing that, the one a search
 * puts first, as long as its name begins with [name]: "Tearmoon Empire" is "Tearmoon Empire (Light
 * Novel)" there.
 */
export async function hardcoverSeries(name: string): Promise<SeriesPage> {
	const wanted = name.trim();
	if (!wanted) return { name: null, books: [] };
	let series = obj(arr((await gql(SERIES_BY_NAME_QUERY, { name: wanted })).series)[0]);
	if (!series) {
		const top = obj(obj(arr(obj(obj((await gql(SERIES_SEARCH_QUERY, { query: wanted })).search)?.results)?.hits)[0])?.document);
		const foundName = str(top?.name);
		const id = num(top?.id);
		if (foundName && id != null && normalize(foundName).startsWith(normalize(wanted))) {
			series = obj(arr((await gql(SERIES_BY_ID_QUERY, { id })).series)[0]);
		}
	}
	const seriesName = line(series?.name);
	if (!series || !seriesName) return { name: null, books: [] };
	return {
		name: seriesName,
		infoUrl: str(series.slug) ? `https://hardcover.app/series/${str(series.slug)}` : undefined,
		books: found(
			arr(series.book_series).map((entry) => {
				const book = obj(obj(entry)?.book);
				const metadata = book ? bookToFound(book) : null;
				return metadata ? { ...metadata, series: seriesName, seriesNumber: num(obj(entry)?.position) } : null;
			}),
		),
	};
}

export interface BookReview {
	id: string;
	reviewer?: string;
	rating?: number;
	text: string;
	hasSpoilers: boolean;
	/** A date in "yyyy-mm-dd", of which only the date is shown. */
	reviewedOn?: string;
	likes: number;
}

export interface BookReviews {
	averageRating?: number;
	ratingsCount: number;
	reviewsCount: number;
	reviews: BookReview[];
}

export async function hardcoverReviews(sourceId: string): Promise<BookReviews> {
	const id = Number(sourceId);
	if (!Number.isInteger(id)) return { ratingsCount: 0, reviewsCount: 0, reviews: [] };
	let data: Json;
	try {
		data = await gql(REVIEWS_QUERY, { id, limit: REVIEWS_LIMIT });
	} catch (e) {
		// A query touching a field the token may not read is refused whole, so when it is only the
		// reviewers' names that are out of reach, settle for the reviews without them.
		if (!(e instanceof LookupError) || e.reason !== 'missingPermission' || !e.scope?.includes(USERS_SCOPE)) throw e;
		data = await gql(ANONYMOUS_REVIEWS_QUERY, { id, limit: REVIEWS_LIMIT });
	}
	const book = obj(data.books_by_pk);
	const reviews = arr(data.user_books).flatMap((row): BookReview[] => {
		const entry = obj(row);
		const reviewId = str(entry?.id);
		const text = str(entry?.review_raw)?.trim() || (str(entry?.review) ? plainText(str(entry?.review)!) : '');
		if (!entry || !reviewId || !text) return [];
		const user = obj(entry.user);
		return [
			{
				id: reviewId,
				reviewer: line(user?.name) ?? line(user?.username),
				rating: (num(entry.rating) ?? 0) > 0 ? num(entry.rating) : undefined,
				text,
				hasSpoilers: entry.review_has_spoilers === true,
				reviewedOn: str(entry.reviewed_at)?.slice(0, 10),
				likes: num(entry.likes_count) ?? 0,
			},
		];
	});
	return {
		averageRating: (num(book?.rating) ?? 0) > 0 ? num(book?.rating) : undefined,
		ratingsCount: num(book?.ratings_count) ?? 0,
		reviewsCount: Math.max(num(book?.reviews_count) ?? 0, reviews.length),
		reviews,
	};
}

export async function hardcoverByIsbn(isbn13: string): Promise<Found[]> {
	const hits = await searchDocuments(isbn13, 1);
	return found(hits.documents.map((doc) => documentToFound(doc, isbn13))).filter((book) => book.isbn13 === isbn13);
}
