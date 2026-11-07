import type { Book, PhysicalReading, ReadingSession, ReadThrough } from '@/types';
import { monthOfEpochDay, yearOfEpochDay } from './dates';
import { alive, inLibrary } from './library';

/**
 * The statistics, worked out from the rows as the Android app's `StatsRepository` does. Nothing is
 * stored aggregated: sessions, read-throughs and paper readings are added up on the spot. A reading
 * on paper counts for a book with no EPUB only, so a book read both ways is not counted twice.
 */

export interface StatsInput {
	books: Book[];
	readThroughs: ReadThrough[];
	sessions: ReadingSession[];
	physical: PhysicalReading[];
}

export interface CompletedBook {
	book: Book;
	finishedOn: number;
	readThroughId: string;
}

const pagesOf = (reading: PhysicalReading) => reading.endPage - reading.startPage;
const sum = (values: number[]) => values.reduce((total, value) => total + value, 0);

function prepare(input: StatsInput) {
	const byId = new Map(input.books.filter(alive).map((book) => [book.id, book]));
	const sessions = input.sessions.filter((row) => alive(row) && byId.has(row.bookId));
	const paper = input.physical.filter((row) => alive(row) && byId.get(row.bookId) && !inLibrary(byId.get(row.bookId)!));
	const completed: CompletedBook[] = input.readThroughs
		.filter((row) => alive(row) && row.outcome === 'COMPLETED' && row.finishedOn != null && byId.has(row.bookId))
		.sort((a, b) => (a.finishedAt ?? 0) - (b.finishedAt ?? 0))
		.map((row) => ({ book: byId.get(row.bookId)!, finishedOn: row.finishedOn!, readThroughId: row.id }));
	return { byId, sessions, paper, completed };
}

/** The ratings of the books among [completed], each book once however often it was read. */
function ratings(completed: CompletedBook[]): number[] {
	const seen = new Set<string>();
	return completed.flatMap(({ book }) => {
		if (seen.has(book.id) || book.rating == null) return [];
		seen.add(book.id);
		return [book.rating];
	});
}

/** Rated books per whole star, index 0 being one star. A half star counts as the next one up. */
function starCounts(rated: number[]): number[] {
	return [1, 2, 3, 4, 5].map((star) => rated.filter((rating) => Math.ceil(rating) === star).length);
}

const average = (values: number[]) => (values.length ? sum(values) / values.length : null);

function spending(purchases: Book[]): Map<string, number> {
	const out = new Map<string, number>();
	for (const book of purchases)
		out.set(book.purchaseCurrency ?? '', (out.get(book.purchaseCurrency ?? '') ?? 0) + (book.purchasePriceMinor ?? 0));
	return out;
}

export interface YearStats {
	year: number;
	goalBooks: number | null;
	completed: CompletedBook[];
	completedPerMonth: number[];
	durationPerMonth: number[];
	pagesPerMonth: number[];
	durationMs: number;
	pages: number;
	ratingAverage: number | null;
	ratingCounts: number[];
	spending: Map<string, number>;
}

export function yearStats(input: StatsInput, year: number, goalBooks: number | null): YearStats {
	const { sessions, paper, completed } = prepare(input);
	const inYear = <T extends { day: number }>(rows: T[]) => rows.filter((row) => yearOfEpochDay(row.day) === year);
	const yearSessions = inYear(sessions);
	const yearPaper = inYear(paper);
	const done = completed.filter((entry) => yearOfEpochDay(entry.finishedOn) === year);
	const rated = ratings(done);
	const months = [...Array(12).keys()];
	const purchases = input.books.filter(
		(book) => alive(book) && book.purchasedOn != null && book.purchasePriceMinor != null && yearOfEpochDay(book.purchasedOn) === year,
	);

	return {
		year,
		goalBooks,
		completed: done,
		completedPerMonth: months.map((month) => done.filter((entry) => monthOfEpochDay(entry.finishedOn) === month).length),
		durationPerMonth: months.map((month) =>
			sum(yearSessions.filter((row) => monthOfEpochDay(row.day) === month).map((row) => row.durationMs)),
		),
		pagesPerMonth: months.map(
			(month) =>
				sum(yearSessions.filter((row) => monthOfEpochDay(row.day) === month).map((row) => row.pages ?? 0)) +
				sum(yearPaper.filter((row) => monthOfEpochDay(row.day) === month).map(pagesOf)),
		),
		durationMs: sum(yearSessions.map((row) => row.durationMs)),
		pages: sum(yearSessions.map((row) => row.pages ?? 0)) + sum(yearPaper.map(pagesOf)),
		ratingAverage: average(rated),
		ratingCounts: starCounts(rated),
		spending: spending(purchases),
	};
}

export interface AllTimeStats {
	since: number | null;
	completed: CompletedBook[];
	booksPerMonth: number;
	years: { year: number; completed: number; durationMs: number }[];
	durationMs: number;
	pages: number;
	daysRead: number;
	ratingAverage: number | null;
	ratingCounts: number[];
	spending: Map<string, number>;
}

export function allTimeStats(input: StatsInput, today: number): AllTimeStats {
	const { sessions, paper, completed } = prepare(input);
	const firstRead = Math.min(...sessions.map((row) => row.day), ...paper.map((row) => row.day));
	const firstDone = Math.min(...completed.map((entry) => entry.finishedOn));
	const since = Number.isFinite(Math.min(firstRead, firstDone)) ? Math.min(firstRead, firstDone) : null;
	const months =
		since == null ? 1 : (yearOfEpochDay(today) - yearOfEpochDay(since)) * 12 + (monthOfEpochDay(today) - monthOfEpochDay(since)) + 1;

	const yearsSeen = [
		...sessions.map((row) => yearOfEpochDay(row.day)),
		...completed.map((entry) => yearOfEpochDay(entry.finishedOn)),
		...paper.map((row) => yearOfEpochDay(row.day)),
	];
	const first = yearsSeen.length ? Math.min(...yearsSeen) : null;
	const last = yearsSeen.length ? Math.max(...yearsSeen, yearOfEpochDay(today)) : null;
	const years =
		first == null || last == null
			? []
			: Array.from({ length: last - first + 1 }, (_, index) => first + index).map((year) => ({
					year,
					completed: completed.filter((entry) => yearOfEpochDay(entry.finishedOn) === year).length,
					durationMs: sum(sessions.filter((row) => yearOfEpochDay(row.day) === year).map((row) => row.durationMs)),
				}));

	const timedDays = new Map<number, number>();
	for (const row of sessions) timedDays.set(row.day, (timedDays.get(row.day) ?? 0) + row.durationMs);
	const days = new Set([...[...timedDays].filter(([, ms]) => ms > 0).map(([day]) => day), ...paper.map((row) => row.day)]);
	const rated = ratings(completed);

	return {
		since,
		completed,
		booksPerMonth: completed.length / Math.max(months, 1),
		years,
		durationMs: sum(sessions.map((row) => row.durationMs)),
		pages: sum(sessions.map((row) => row.pages ?? 0)) + sum(paper.map(pagesOf)),
		daysRead: days.size,
		ratingAverage: average(rated),
		ratingCounts: starCounts(rated),
		spending: spending(input.books.filter((book) => alive(book) && book.purchasedOn != null && book.purchasePriceMinor != null)),
	};
}

export interface CalendarBook {
	book: Book;
	durationMs: number;
	completed: boolean;
	onPaper: boolean;
}

/** Every day from [fromDay] up to [toDay] on which something was read or finished, finished books first. */
export function calendarDays(input: StatsInput, fromDay: number, toDay: number): Map<number, CalendarBook[]> {
	const { byId, sessions, paper, completed } = prepare(input);
	const within = (day: number) => day >= fromDay && day < toDay;
	const days = new Map<number, Map<string, CalendarBook>>();
	const entry = (day: number, bookId: string) => {
		const books = days.get(day) ?? new Map<string, CalendarBook>();
		days.set(day, books);
		const existing = books.get(bookId) ?? { book: byId.get(bookId)!, durationMs: 0, completed: false, onPaper: false };
		books.set(bookId, existing);
		return existing;
	};
	for (const row of sessions.filter((row) => within(row.day))) entry(row.day, row.bookId).durationMs += row.durationMs;
	for (const row of paper.filter((row) => within(row.day))) entry(row.day, row.bookId).onPaper = true;
	for (const done of completed.filter((entry) => within(entry.finishedOn))) entry(done.finishedOn, done.book.id).completed = true;

	return new Map(
		[...days].map(([day, books]) => [
			day,
			[...books.values()].sort((a, b) => Number(b.completed) - Number(a.completed) || b.durationMs - a.durationMs),
		]),
	);
}

/** Minutes read on [day], for the daily goal. */
export function minutesOn(input: StatsInput, day: number): number {
	const { sessions } = prepare(input);
	return Math.round(sum(sessions.filter((row) => row.day === day).map((row) => row.durationMs)) / 60_000);
}
