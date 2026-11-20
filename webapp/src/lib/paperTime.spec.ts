import { describe, expect, it } from 'vitest';
import type { Book, PhysicalReading, ReadingSession } from '@/types';
import { timelineOf, withPaperTime, type BookReading } from './stats';

const sync = { createdAt: 0, updatedAt: 0 };
const paper: Book = { id: 'paper', title: 'Children of Time', sync } as Book;
const epub: Book = { id: 'epub', title: 'Piranesi', fileHash: 'x', sync } as Book;
const byId = new Map([paper, epub].map((book) => [book.id, book]));

const session = (bookId: string, startedAt: number, minutes: number): ReadingSession => ({
	id: `s-${bookId}-${startedAt}`,
	bookId,
	startedAt,
	endedAt: startedAt + minutes * 60_000,
	durationMs: minutes * 60_000,
	day: 0,
	startProgression: 0,
	endProgression: 0,
	sync,
});
const pages = (recordedAt: number, startPage: number, endPage: number): PhysicalReading =>
	({ id: `r-${recordedAt}`, bookId: 'paper', recordedAt, day: 0, startPage, endPage, sync }) as PhysicalReading;

describe('timelineOf', () => {
	it('tells a sitting timed in the reading mode by the pages it turned', () => {
		const entries = timelineOf([session('paper', 1_000, 25), session('epub', 9_000_000, 10)], [pages(4_000, 40, 61)], byId);
		expect(entries).toEqual([
			{ kind: 'pages', book: paper, at: 4_000, startPage: 40, endPage: 61, durationMs: 25 * 60_000 },
			{ kind: 'session', book: epub, at: 9_000_000, durationMs: 10 * 60_000, chapter: undefined },
		]);
	});
});

describe('withPaperTime', () => {
	it('puts the time the reading mode kept on the paper card', () => {
		const row = (book: Book, durationMs: number, pageCount: number, physical: boolean): BookReading => ({
			book,
			durationMs,
			pages: pageCount,
			progressGained: 0,
			physical,
		});
		const read = row(epub, 600_000, 12, false);
		const cards = withPaperTime([row(paper, 1_800_000, 0, true), read], [row(paper, 0, 20, true)]);
		expect(cards).toEqual([read, row(paper, 1_800_000, 20, true)]);
	});
});
