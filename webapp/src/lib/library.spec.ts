import { describe, expect, it } from 'vitest';
import { TABLE_NAMES } from '../../shared/tables';
import type { Book, Changes } from '@/types';
import { mergeBook } from '../../shared/tables';
import * as ops from './library';

function tables(rows: Changes = {}): ops.Tables {
	const out = Object.fromEntries(TABLE_NAMES.map((name) => [name, new Map()])) as unknown as ops.Tables;
	for (const name of TABLE_NAMES)
		for (const row of (rows[name] ?? []) as never[]) (out[name] as Map<string, unknown>).set(ops.keyOf(name, row), row);
	return out;
}

const book = (extra: Partial<Book> = {}): Book => ({
	id: 'b1',
	title: 'The Book',
	state: 'PLANNED',
	acquisition: 'PURCHASED',
	progression: 0,
	sync: { createdAt: 1, updatedAt: 1 },
	...extra,
});

/** Runs one operation the way the store does and returns the rows it changed, then the rows after it. */
function run(base: ops.Tables, operation: (tx: ops.Tx) => void, now = 10 * 86_400_000) {
	const tx = new ops.Tx(base, now);
	operation(tx);
	const changes = tx.changes();
	const after = tables();
	for (const name of TABLE_NAMES) {
		for (const [key, row] of base[name] as Map<string, never>) (after[name] as Map<string, unknown>).set(key, row);
		for (const row of (changes[name] ?? []) as never[]) (after[name] as Map<string, unknown>).set(ops.keyOf(name, row), row);
	}
	return { changes, after };
}

describe('setState', () => {
	it('opens a read-through when reading starts and closes it on completion', () => {
		const start = run(tables({ books: [book()] }), (tx) => ops.setState(tx, 'b1', 'READING'));
		const [opened] = start.changes.readThroughs!;
		expect(opened).toMatchObject({ bookId: 'b1', number: 1, startedAt: 10 * 86_400_000 });
		expect(opened.finishedAt).toBeUndefined();

		const done = run(start.after, (tx) => ops.setState(tx, 'b1', 'COMPLETED'), 12 * 86_400_000);
		expect(done.changes.readThroughs![0]).toMatchObject({ id: opened.id, outcome: 'COMPLETED', finishedAt: 12 * 86_400_000 });
		expect(done.changes.books![0]).toMatchObject({ state: 'COMPLETED', progression: 1 });
	});

	it('takes a completion back by reopening the same read-through', () => {
		const reading = run(tables({ books: [book()] }), (tx) => ops.setState(tx, 'b1', 'READING')).after;
		const completed = run(reading, (tx) => ops.setState(tx, 'b1', 'COMPLETED')).after;
		const back = run(completed, (tx) => ops.setState(tx, 'b1', 'READING'));
		const [reopened] = back.changes.readThroughs!;
		expect(reopened.number).toBe(1);
		expect(reopened.outcome).toBeUndefined();
		expect(reopened.finishedOn).toBeUndefined();
	});

	it('drops a read-through nobody read in when the book goes back to planned', () => {
		const reading = run(tables({ books: [book()] }), (tx) => ops.setState(tx, 'b1', 'READING')).after;
		const planned = run(reading, (tx) => ops.setState(tx, 'b1', 'PLANNED'));
		expect(planned.changes.readThroughs![0].sync.deletedAt).toBe(10 * 86_400_000);
	});

	it('keeps a wishlist entry planned', () => {
		const { changes } = run(tables({ books: [book({ acquisition: 'WISHLIST' })] }), (tx) => ops.setState(tx, 'b1', 'READING'));
		expect(changes).toEqual({});
	});

	it('stamps every changed row with the time of the edit', () => {
		const { changes } = run(tables({ books: [book()] }), (tx) => ops.setState(tx, 'b1', 'READING'), 5000);
		expect(changes.books![0].sync).toEqual({ createdAt: 1, updatedAt: 5000 });
	});
});

describe('readAgain', () => {
	it('starts a second read-through from the beginning', () => {
		const reading = run(tables({ books: [book({ progression: 0.4 })] }), (tx) => ops.setState(tx, 'b1', 'READING')).after;
		const completed = run(reading, (tx) => ops.setState(tx, 'b1', 'COMPLETED')).after;
		const again = run(completed, (tx) => ops.readAgain(tx, 'b1'));
		expect(again.changes.readThroughs!.map((row) => row.number)).toEqual([2]);
		expect(again.changes.books![0]).toMatchObject({ state: 'READING', progression: 0 });
	});
});

describe('setPhysicalProgress', () => {
	it('records the pages read and completes the book on its last page', () => {
		const first = run(tables({ books: [book({ pageCount: 300 })] }), (tx) => ops.setPhysicalProgress(tx, 'b1', 120, null));
		expect(first.changes.physicalReadings![0]).toMatchObject({ startPage: 0, endPage: 120, day: 10 });
		expect(first.after.books.get('b1')!.state).toBe('READING');

		const last = run(first.after, (tx) => ops.setPhysicalProgress(tx, 'b1', 300, null));
		expect(last.after.books.get('b1')!.state).toBe('COMPLETED');
	});

	it('removes what was recorded past a page taken back', () => {
		const forward = run(tables({ books: [book({ pageCount: 300 })] }), (tx) => ops.setPhysicalProgress(tx, 'b1', 100, null)).after;
		const further = run(forward, (tx) => ops.setPhysicalProgress(tx, 'b1', 200, null)).after;
		const back = run(further, (tx) => ops.setPhysicalProgress(tx, 'b1', 150, null)).after;
		const readings = [...back.physicalReadings.values()].filter(ops.alive).map((row) => [row.startPage, row.endPage]);
		expect(readings.sort()).toEqual([
			[0, 100],
			[100, 150],
		]);
	});
});

describe('deleteBook', () => {
	it('tombstones the book and everything that hangs off it', () => {
		const base = tables({
			books: [book()],
			notes: [{ id: 'n1', bookId: 'b1', text: 'x', sync: { createdAt: 1, updatedAt: 1 } }],
			shelfBooks: [{ shelfId: 's1', bookId: 'b1', sync: { createdAt: 1, updatedAt: 1 } }],
		});
		const { changes } = run(base, (tx) => ops.deleteBook(tx, 'b1'));
		expect(changes.books![0].sync.deletedAt).toBeDefined();
		expect(changes.notes![0].sync.deletedAt).toBeDefined();
		expect(changes.shelfBooks![0].sync.deletedAt).toBeDefined();
	});
});

describe('setPurchase', () => {
	it('sends a book without an EPUB back to the wishlist, planned and unfavourited', () => {
		const base = tables({ books: [book({ state: 'PAUSED', isFavorite: true, purchasePriceMinor: 999 })] });
		const { after } = run(base, (tx) =>
			ops.setPurchase(tx, 'b1', {
				acquisition: 'WISHLIST',
				priceMinor: null,
				currency: null,
				purchasedOn: null,
				location: null,
				memo: null,
			}),
		);
		expect(after.books.get('b1')).toMatchObject({ acquisition: 'WISHLIST', state: 'PLANNED', isFavorite: false });
		expect(after.books.get('b1')!.purchasePriceMinor).toBeUndefined();
	});

	it('keeps a book with an EPUB as downloaded', () => {
		const { after } = run(tables({ books: [book({ fileHash: 'abc' })] }), (tx) =>
			ops.setPurchase(tx, 'b1', {
				acquisition: 'WISHLIST',
				priceMinor: null,
				currency: null,
				purchasedOn: null,
				location: null,
				memo: null,
			}),
		);
		expect(after.books.get('b1')!.acquisition).toBe('DOWNLOADED');
	});
});

describe('shelves', () => {
	it('puts a book back on a shelf by writing the row over', () => {
		const on = run(tables(), (tx) => ops.setOnShelf(tx, 's1', 'b1', true)).after;
		const off = run(on, (tx) => ops.setOnShelf(tx, 's1', 'b1', false)).after;
		expect(off.shelfBooks.get('s1|b1')!.sync.deletedAt).toBeDefined();
		const back = run(off, (tx) => ops.setOnShelf(tx, 's1', 'b1', true)).after;
		expect(back.shelfBooks.get('s1|b1')!.sync.deletedAt).toBeUndefined();
	});
});

describe('addFound', () => {
	it('returns the book already added from the same record', () => {
		const found: ops.Found = { source: 'HARDCOVER', sourceId: '42', title: 'Found', authors: ['A'] };
		const first = run(tables(), (tx) => ops.addFound(tx, found, 'WISHLIST'));
		const id = first.changes.books![0].id;
		let again = '';
		run(first.after, (tx) => (again = ops.addFound(tx, found, 'WISHLIST')));
		expect(again).toBe(id);
	});
});

describe('reading position clock', () => {
	it('moves lastReadAt when completing changes the position, and on reading again', () => {
		const reading = run(tables({ books: [book({ progression: 0.4 })] }), (tx) => ops.setState(tx, 'b1', 'READING')).after;
		const completed = run(reading, (tx) => ops.setState(tx, 'b1', 'COMPLETED'), 7000);
		expect(completed.changes.books![0].lastReadAt).toBe(7000);
		const again = run(completed.after, (tx) => ops.readAgain(tx, 'b1'), 8000);
		expect(again.changes.books![0].lastReadAt).toBe(8000);
	});
});

describe('mergeBook', () => {
	const at = (updatedAt: number, lastReadAt: number, extra: Partial<Book> = {}) =>
		book({ ...extra, lastReadAt, sync: { createdAt: 1, updatedAt } });

	it('takes the reading position and the rest each from the later side', () => {
		const merged = mergeBook(at(1000, 5000, { progression: 0.6, rating: 3 }), at(2000, 900, { progression: 0.1, rating: 5 }))!;
		expect(merged.fromRemote).toBe(false);
		expect(merged.book).toMatchObject({ rating: 5, progression: 0.6, lastReadAt: 5000, sync: { updatedAt: 2000 } });
	});

	it('takes nothing when the local copy is later on both counts', () => {
		expect(mergeBook(at(3000, 3000), at(2000, 2000))).toBeNull();
	});
});
