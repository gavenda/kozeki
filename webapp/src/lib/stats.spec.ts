import { describe, expect, it } from 'vitest';
import type { Book, ReadingSession, ReadThrough } from '@/types';
import { epochDayOfDate } from './dates';
import { allTimeStats, calendarDays, dayStats, longestFinished, periodStats, weekStartOf, yearStats } from './stats';

const stamp = { createdAt: 1, updatedAt: 1 };
const day = (y: number, m: number, d: number) => epochDayOfDate(y, m - 1, d);
const book = (id: string, extra: Partial<Book> = {}): Book => ({ id, title: id, sync: stamp, ...extra });
const done = (id: string, bookId: string, finishedOn: number): ReadThrough => ({
	id,
	bookId,
	number: 1,
	startedAt: 0,
	finishedAt: finishedOn * 86_400_000,
	finishedOn,
	outcome: 'COMPLETED',
	sync: stamp,
});
const session = (bookId: string, on: number, minutes: number, pages = 0): ReadingSession => ({
	id: `${bookId}-${on}-${minutes}`,
	bookId,
	startedAt: on * 86_400_000,
	endedAt: on * 86_400_000,
	durationMs: minutes * 60_000,
	day: on,
	startProgression: 0,
	endProgression: 0,
	pages,
	sync: stamp,
});

describe('yearStats', () => {
	it('counts books finished, time and pages by month', () => {
		const input = {
			books: [book('a', { rating: 4 }), book('b', { rating: 2.5 })],
			readThroughs: [done('r1', 'a', day(2026, 3, 5)), done('r2', 'b', day(2026, 3, 20)), done('r3', 'a', day(2025, 1, 1))],
			sessions: [session('a', day(2026, 3, 1), 30, 40), session('b', day(2026, 4, 1), 15, 10)],
			physical: [],
		};
		const stats = yearStats(input, 2026, 12);
		expect(stats.completed.map((entry) => entry.book.id)).toEqual(['a', 'b']);
		expect(stats.completedPerMonth[2]).toBe(2);
		expect(stats.durationMs).toBe(45 * 60_000);
		expect(stats.pagesPerMonth[2]).toBe(40);
		expect(stats.ratingAverage).toBe(3.25);
		expect(stats.ratingCounts).toEqual([0, 0, 1, 1, 0]);
	});

	it('leaves out removed books and what was read of them', () => {
		const input = {
			books: [book('a', { sync: { ...stamp, deletedAt: 5 } })],
			readThroughs: [done('r1', 'a', day(2026, 3, 5))],
			sessions: [session('a', day(2026, 3, 1), 30)],
			physical: [],
		};
		expect(yearStats(input, 2026, null).completed).toEqual([]);
		expect(yearStats(input, 2026, null).durationMs).toBe(0);
	});

	it('counts paper pages only for books with no EPUB', () => {
		const reading = (bookId: string) => ({
			id: bookId,
			bookId,
			recordedAt: 0,
			day: day(2026, 1, 2),
			startPage: 0,
			endPage: 50,
			sync: stamp,
		});
		const input = {
			books: [book('paper'), book('epub', { fileHash: 'x' })],
			readThroughs: [],
			sessions: [],
			physical: [reading('paper'), reading('epub')],
		};
		expect(yearStats(input, 2026, null).pages).toBe(50);
	});
});

describe('allTimeStats', () => {
	it('counts days read and the years since the first', () => {
		const input = {
			books: [book('a')],
			readThroughs: [done('r1', 'a', day(2025, 6, 1))],
			sessions: [session('a', day(2025, 5, 1), 10), session('a', day(2025, 5, 1), 5), session('a', day(2026, 1, 1), 20)],
			physical: [],
		};
		const stats = allTimeStats(input, day(2026, 2, 1));
		expect(stats.since).toBe(day(2025, 5, 1));
		expect(stats.daysRead).toBe(2);
		expect(stats.years.map((entry) => entry.year)).toEqual([2025, 2026]);
		expect(stats.completed).toHaveLength(1);
	});
});

describe('calendarDays', () => {
	it('puts finished books first on their day', () => {
		const on = day(2026, 3, 5);
		const input = {
			books: [book('a'), book('b')],
			readThroughs: [done('r1', 'b', on)],
			sessions: [session('a', on, 60), session('b', on, 5)],
			physical: [],
		};
		const days = calendarDays(input, day(2026, 3, 1), day(2026, 4, 1));
		expect(days.get(on)!.map((entry) => entry.book.id)).toEqual(['b', 'a']);
	});
});

describe('days and periods', () => {
	it('starts a week on the day asked for', () => {
		// 8 November 2026 is a Sunday.
		const sunday = day(2026, 11, 8);
		expect(weekStartOf(sunday, 1)).toBe(day(2026, 11, 2));
		expect(weekStartOf(sunday, 0)).toBe(sunday);
		expect(weekStartOf(day(2026, 11, 14), 0)).toBe(sunday);
	});

	it('adds up a day, its week and its timeline', () => {
		const today = day(2026, 11, 4);
		const input = {
			books: [book('a', { positionCount: 200, fileHash: 'x' }), book('b')],
			readThroughs: [done('r1', 'a', today)],
			sessions: [session('a', today, 30, 20), session('a', today - 1, 10, 5)],
			physical: [{ id: 'p', bookId: 'b', recordedAt: today * 86_400_000 + 5, day: today, startPage: 10, endPage: 25, sync: stamp }],
		};
		const stats = dayStats(input, today, 1, 20);
		expect(stats.durationMs).toBe(30 * 60_000);
		expect(stats.pages).toBe(35);
		expect(stats.books.map((reading) => [reading.book.id, reading.physical, reading.pages])).toEqual([
			['a', false, 20],
			['b', true, 15],
		]);
		expect(stats.books[0].progressGained).toBe(0.1);
		expect(stats.completed.map((entry) => entry.book.id)).toEqual(['a']);
		expect(stats.timeline.map((entry) => entry.kind)).toEqual(['session', 'pages']);
		expect(stats.week).toHaveLength(7);
		expect(stats.week.find((entry) => entry.day === today - 1)?.durationMs).toBe(10 * 60_000);
	});

	it('counts the days read and the days the goal was met', () => {
		const start = day(2026, 11, 2);
		const input = {
			books: [book('a')],
			readThroughs: [],
			sessions: [session('a', start, 25), session('a', start + 2, 5)],
			physical: [],
		};
		const stats = periodStats(input, start, start + 6, 20);
		expect(stats.days).toHaveLength(7);
		expect(stats.daysRead).toBe(2);
		expect(stats.goalDaysMet).toBe(1);
	});

	it('lists the longest books of each kind once, by the length that counts for it', () => {
		const completed = [
			{ book: book('paper', { pageCount: 300, physicalPageCount: 320 }), finishedOn: 1, readThroughId: 'a' },
			{ book: book('paper', { pageCount: 300, physicalPageCount: 320 }), finishedOn: 2, readThroughId: 'b' },
			{ book: book('epub', { fileHash: 'x', positionCount: 500 }), finishedOn: 3, readThroughId: 'c' },
			{ book: book('short', { pageCount: 0 }), finishedOn: 4, readThroughId: 'd' },
		];
		expect(longestFinished(completed, 'PHYSICAL')).toEqual([{ book: completed[0].book, length: 320 }]);
		expect(longestFinished(completed, 'EPUB')).toEqual([{ book: completed[2].book, length: 500 }]);
	});
});
