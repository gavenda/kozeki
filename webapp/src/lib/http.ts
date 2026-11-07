import { auth0 } from './auth0';

/** An error carrying the API's status; 0 when the request got no answer at all. */
export class ApiError extends Error {
	readonly status: number;

	constructor(status: number, message: string) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
	}
}

async function token(): Promise<string | null> {
	if (!auth0.isAuthenticated.value) return null;
	try {
		return (await auth0.getAccessTokenSilently()) ?? null;
	} catch {
		return null;
	}
}

/** The one `fetch` every API call goes through, with the access token on it. */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
	const bearer = await token();
	let response: Response;
	try {
		response = await fetch(`/api${path}`, {
			...init,
			headers: {
				...(bearer ? { authorization: `Bearer ${bearer}` } : {}),
				...(init.headers as Record<string, string> | undefined),
			},
		});
	} catch {
		throw new ApiError(0, 'The server cannot be reached.');
	}
	if (response.status === 401) {
		// The session the server saw is over; signing out locally sends the router to the sign-in.
		void auth0.logout({ openUrl: false });
	}
	if (!response.ok) {
		const body = (await response.json().catch(() => null)) as { error?: string } | null;
		throw new ApiError(response.status, body?.error ?? `Request failed (${response.status}).`);
	}
	return response;
}

export async function apiJson<T>(path: string, body: unknown): Promise<T> {
	const response = await apiFetch(path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
	return (await response.json()) as T;
}
