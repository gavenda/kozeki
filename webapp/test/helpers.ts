import { SELF } from 'cloudflare:test';
import type { Row, TableName } from '../shared/tables';
import type { SyncResponse } from '../server/sync';
import { mintToken, OTHER_SUBJECT, publishJwks, type TokenOverrides } from './tokens';

const BASE = 'https://kozeki.test/api';

export function request(path: string, init: RequestInit = {}): Promise<Response> {
	return SELF.fetch(`${BASE}${path}`, init);
}

/** A caller presenting a freshly minted access token, as the apps do with Auth0's. */
export async function authedClient(overrides: TokenOverrides = {}) {
	await publishJwks();
	const token = await mintToken(overrides);
	return (path: string, init: RequestInit = {}): Promise<Response> =>
		request(path, {
			...init,
			headers: {
				'content-type': 'application/json',
				authorization: `Bearer ${token}`,
				...(init.headers as Record<string, string> | undefined),
			},
		});
}

export type Call = Awaited<ReturnType<typeof authedClient>>;

export const otherClient = (): Promise<Call> => authedClient({ sub: OTHER_SUBJECT });

export async function json<T>(response: Response): Promise<T> {
	return (await response.json()) as T;
}

/** Posts one sync request and returns the answer, failing on anything but 200. */
export async function sync(
	call: Call,
	cursor: number,
	push: Partial<Record<TableName, Row[]>> = {},
	limit?: number,
): Promise<SyncResponse> {
	const response = await call('/sync', { method: 'POST', body: JSON.stringify({ cursor, push, limit }) });
	if (response.status !== 200) throw new Error(`sync failed (${response.status}): ${await response.text()}`);
	return json<SyncResponse>(response);
}

export const stamp = (updatedAt: number, deletedAt?: number) => ({ createdAt: 1, updatedAt, ...(deletedAt ? { deletedAt } : {}) });

export function book(id: string, updatedAt = 1000, extra: Record<string, unknown> = {}): Row {
	return {
		id,
		title: `Book ${id}`,
		authors: ['Ui'],
		state: 'READING',
		isFavorite: false,
		progression: 0.25,
		...extra,
		sync: stamp(updatedAt),
	};
}
