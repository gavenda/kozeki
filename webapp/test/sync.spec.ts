import { describe, expect, it } from 'vitest';
import { authedClient, book, json, otherClient, request, stamp, sync } from './helpers';

describe('POST /api/sync', () => {
	it('refuses a request without a token', async () => {
		const response = await request('/sync', { method: 'POST', body: JSON.stringify({ cursor: 0 }) });
		expect(response.status).toBe(401);
	});

	it('starts empty', async () => {
		const call = await authedClient();
		expect(await sync(call, 0)).toEqual({ rows: {}, cursor: 0, more: false });
	});

	it('returns pushed rows in the shape they were sent', async () => {
		const call = await authedClient();
		const sent = book('b1', 1000, { seriesNumber: 1.5, authorRefs: [{ name: 'Ui', id: '7' }], customCover: true, isbn13: '9780000000000' });
		const answer = await sync(call, 0, { books: [sent] });

		expect(answer.rows.books).toEqual([{ ...sent, detailsEdited: false }]);
		expect(answer.cursor).toBeGreaterThan(0);
	});

	it('drops fields it does not keep, such as a comic column', async () => {
		const call = await authedClient();
		const answer = await sync(call, 0, { books: [book('b1', 1000, { newChapters: 3, sourceMemo: '{}' })] });
		expect(answer.rows.books![0]).not.toHaveProperty('newChapters');
		expect(answer.rows.books![0]).not.toHaveProperty('sourceMemo');
	});

	it('refuses comics', async () => {
		const call = await authedClient();
		const response = await call('/sync', {
			method: 'POST',
			body: JSON.stringify({ cursor: 0, push: { books: [book('c1', 1, { source: 'COMICS' })] } }),
		});
		expect(response.status).toBe(400);
	});

	it('refuses a row missing a required field', async () => {
		const call = await authedClient();
		const response = await call('/sync', {
			method: 'POST',
			body: JSON.stringify({ cursor: 0, push: { notes: [{ id: 'n1', bookId: 'b1', sync: stamp(1) }] } }),
		});
		expect(response.status).toBe(400);
	});

	it('only returns what changed after the cursor', async () => {
		const call = await authedClient();
		const first = await sync(call, 0, { books: [book('b1')] });
		const second = await sync(call, first.cursor, { notes: [{ id: 'n1', bookId: 'b1', text: 'Mind the spine.', sync: stamp(1) }] });

		expect(second.rows.books).toBeUndefined();
		expect(second.rows.notes?.map((note) => note.id)).toEqual(['n1']);
		expect(await sync(call, second.cursor)).toEqual({ rows: {}, cursor: second.cursor, more: false });
	});

	it('keeps the newer edit and sends it back to a device that pushed an older one', async () => {
		const call = await authedClient();
		const { cursor } = await sync(call, 0, { books: [book('b1', 2000, { title: 'Newer' })] });

		const answer = await sync(call, cursor, { books: [book('b1', 1500, { title: 'Older' })] });
		expect(answer.rows.books?.map((row) => row.title)).toEqual(['Newer']);

		const everything = await sync(call, 0);
		expect(everything.rows.books?.map((row) => row.title)).toEqual(['Newer']);
	});

	it('carries deletions as tombstones', async () => {
		const call = await authedClient();
		const { cursor } = await sync(call, 0, { books: [book('b1', 1000)] });
		const deleted = { ...book('b1', 1001), sync: stamp(1001, 1001) };
		const answer = await sync(call, cursor, { books: [deleted] });
		expect(answer.rows.books![0].sync.deletedAt).toBe(1001);
	});

	it('keys shelf memberships, goals and settings by their own fields', async () => {
		const call = await authedClient();
		const answer = await sync(call, 0, {
			shelves: [{ id: 's1', name: 'Favourites', sync: stamp(1) }],
			shelfBooks: [{ shelfId: 's1', bookId: 'b1', sync: stamp(1) }],
			yearlyGoals: [{ year: 2026, books: 24, sync: stamp(1) }],
			settings: [{ key: 'dailyGoalMinutes', value: 30, sync: stamp(1) }],
		});
		expect(answer.rows.shelfBooks).toHaveLength(1);
		expect(answer.rows.yearlyGoals![0]).toMatchObject({ year: 2026, books: 24 });
		expect(answer.rows.settings![0]).toMatchObject({ key: 'dailyGoalMinutes', value: 30 });

		const later = await sync(call, answer.cursor, { yearlyGoals: [{ year: 2026, books: 30, sync: stamp(2) }] });
		expect(later.rows.yearlyGoals).toEqual([{ year: 2026, books: 30, sync: stamp(2) }]);
	});

	it('pages a large pull in rev order', async () => {
		const call = await authedClient();
		await sync(call, 0, {
			books: Array.from({ length: 5 }, (_, index) => book(`b${index}`)),
			notes: Array.from({ length: 4 }, (_, index) => ({ id: `n${index}`, bookId: 'b0', text: 'x', sync: stamp(1) })),
		});

		const seen: string[] = [];
		let cursor = 0;
		let more = true;
		while (more) {
			const page = await sync(call, cursor, {}, 4);
			seen.push(...(page.rows.books ?? []).map((row) => row.id as string), ...(page.rows.notes ?? []).map((row) => row.id as string));
			expect(page.cursor).toBeGreaterThan(cursor);
			({ cursor, more } = page);
		}
		expect(seen.sort()).toEqual(['b0', 'b1', 'b2', 'b3', 'b4', 'n0', 'n1', 'n2', 'n3']);
	});

	it('refuses an oversized push', async () => {
		const call = await authedClient();
		const response = await call('/sync', {
			method: 'POST',
			body: JSON.stringify({ cursor: 0, push: { books: Array.from({ length: 501 }, (_, index) => book(`b${index}`)) } }),
		});
		expect(response.status).toBe(400);
	});

	it("keeps one user's rows from another", async () => {
		const mine = await authedClient();
		const theirs = await otherClient();
		await sync(mine, 0, { books: [book('b1', 1000, { title: 'Mine' })] });

		expect((await sync(theirs, 0)).rows).toEqual({});

		// The same id under another account is a separate row.
		await sync(theirs, 0, { books: [book('b1', 5000, { title: 'Theirs' })] });
		const answer = await sync(mine, 0);
		expect(answer.rows.books?.map((row) => row.title)).toEqual(['Mine']);
	});

	it('answers 400 for a body that is not JSON', async () => {
		const call = await authedClient();
		const response = await call('/sync', { method: 'POST', body: 'nope' });
		expect(response.status).toBe(400);
		expect(await json<{ error: string }>(response)).toHaveProperty('error');
	});
});

describe('reading position', () => {
	it('keeps the later page turn when an older copy of the book is edited elsewhere', async () => {
		const call = await authedClient();
		// The phone read on: its reading position is newer, its details are not.
		const { cursor } = await sync(call, 0, { books: [book('b1', 1000, { progression: 0.6, lastReadAt: 5000, rating: 3 })] });
		// The web rated the book from a copy that still had the old position.
		const answer = await sync(call, cursor, { books: [book('b1', 2000, { progression: 0.1, lastReadAt: 900, rating: 5 })] });

		expect(answer.rows.books).toHaveLength(1);
		expect(answer.rows.books![0]).toMatchObject({ rating: 5, progression: 0.6, lastReadAt: 5000, sync: { updatedAt: 2000 } });
	});

	it('keeps a later edit when an older copy turns a page', async () => {
		const call = await authedClient();
		const { cursor } = await sync(call, 0, { books: [book('b1', 2000, { rating: 5, lastReadAt: 100 })] });
		const answer = await sync(call, cursor, { books: [book('b1', 1000, { rating: 3, progression: 0.7, lastReadAt: 3000 })] });
		expect(answer.rows.books![0]).toMatchObject({ rating: 5, progression: 0.7, lastReadAt: 3000 });
	});

	it('sends nothing back for a row taken as it was sent', async () => {
		const call = await authedClient();
		const { cursor } = await sync(call, 0, { books: [book('b1', 1000)] });
		const answer = await sync(call, cursor, { books: [book('b1', 2000, { lastReadAt: 2000 })] });
		// The pull brings the new version once, and nothing comes back as kept.
		expect(answer.rows.books).toHaveLength(1);
	});
});

describe('limits', () => {
	it('refuses a cover file name that is a path', async () => {
		const call = await authedClient();
		const response = await call('/sync', {
			method: 'POST',
			body: JSON.stringify({ cursor: 0, push: { books: [book('b1', 1, { coverFile: '../../databases/kozeki.db' })] } }),
		});
		expect(response.status).toBe(400);
	});

	it('refuses a body over the limit before reading it', async () => {
		const call = await authedClient();
		const response = await call('/sync', { method: 'POST', body: 'x'.repeat(4 * 1024 * 1024 + 1) });
		expect(response.status).toBe(413);
	});

	it('writes many rows of one table in a single request', async () => {
		const call = await authedClient();
		const sessions = Array.from({ length: 300 }, (_, index) => ({
			id: `s${index}`,
			bookId: 'b1',
			startedAt: index,
			endedAt: index + 1,
			durationMs: 60_000,
			day: 20_000,
			startProgression: 0,
			endProgression: 0.1,
			sync: stamp(1),
		}));
		await sync(call, 0, { readingSessions: sessions });
		let count = 0;
		let cursor = 0;
		let more = true;
		while (more) {
			const page = await sync(call, cursor);
			count += page.rows.readingSessions?.length ?? 0;
			({ cursor, more } = page);
		}
		expect(count).toBe(300);
	});
});
