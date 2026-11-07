import { HARDCOVER_CALLBACK } from './auth0';
import { apiFetch } from './http';
import type { Found } from './library';

/**
 * The browser's own Hardcover sign-in: the web app's OAuth client (authorization code with PKCE, no
 * secret), with the token kept in this browser. Hardcover's endpoints do not answer other origins,
 * so the token exchange and the API go through the Worker (`/api/hardcover/*`), which stores nothing.
 */
const CLIENT_ID = import.meta.env.VITE_HARDCOVER_CLIENT_ID;
const AUTHORIZE = 'https://hardcover.app/oauth2/authorize';
const ISSUER = 'https://api.hardcover.app';
const SCOPES = 'read:catalog';
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
	sessionStorage.setItem(PENDING, JSON.stringify({ state, verifier }));
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
	localStorage.setItem(STORE, JSON.stringify(session));
	return session;
}

/** Finishes the sign-in Hardcover sent the browser back from. Returns false when it was refused or cannot be trusted. */
export async function completeHardcoverSignIn(params: URLSearchParams): Promise<boolean> {
	const pending = read<{ state: string; verifier: string }>(PENDING, sessionStorage);
	sessionStorage.removeItem(PENDING);
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
	if (!fresh) localStorage.removeItem(STORE);
	return fresh?.accessToken ?? null;
}

export function hardcoverSignOut(): void {
	const session = read<Session>(STORE);
	localStorage.removeItem(STORE);
	if (session?.refreshToken) {
		void apiFetch('/hardcover/revoke', {
			method: 'POST',
			headers: { 'content-type': 'application/x-www-form-urlencoded' },
			body: new URLSearchParams({ token: session.refreshToken, token_type_hint: 'refresh_token', client_id: CLIENT_ID! }).toString(),
		}).catch(() => undefined);
	}
}

const SEARCH_QUERY =
	'query Search($query: String!, $page: Int!, $perPage: Int!) { search(query: $query, query_type: "Book", per_page: $perPage, page: $page) { results } }';

interface Document {
	id?: string;
	title?: string;
	subtitle?: string;
	slug?: string;
	author_names?: string[];
	description?: string;
	release_date?: string;
	release_year?: number;
	pages?: number;
	isbns?: string[];
	genres?: string[];
	image?: { url?: string };
	featured_series?: { position?: number; series?: { name?: string } };
}

export async function hardcoverSearch(query: string): Promise<Found[]> {
	const token = await accessToken();
	if (!token) throw new Error('Signed out of Hardcover.');
	const response = await apiFetch('/hardcover/graphql', {
		method: 'POST',
		headers: { 'content-type': 'application/json', 'X-Hardcover-Authorization': `Bearer ${token}` },
		body: JSON.stringify({ query: SEARCH_QUERY, variables: { query, page: 1, perPage: 20 } }),
	});
	const body = (await response.json()) as { data?: { search?: { results?: { hits?: { document: Document }[] } } } };
	return (body.data?.search?.results?.hits ?? []).flatMap(({ document: doc }) => {
		if (!doc.id || !doc.title) return [];
		const series = doc.featured_series?.series?.name;
		return [
			{
				source: 'HARDCOVER' as const,
				sourceId: String(doc.id),
				title: doc.title,
				subtitle: doc.subtitle,
				authors: doc.author_names ?? [],
				description: doc.description || undefined,
				series,
				seriesNumber: series ? doc.featured_series?.position : undefined,
				publishedDate: doc.release_date ?? (doc.release_year ? String(doc.release_year) : undefined),
				pageCount: doc.pages || undefined,
				isbn13: doc.isbns?.find((isbn) => isbn.length === 13),
				categories: doc.genres,
				coverUrl: doc.image?.url?.startsWith('https://') ? doc.image.url : undefined,
				infoUrl: doc.slug ? `https://hardcover.app/books/${doc.slug}` : undefined,
			},
		];
	});
}
