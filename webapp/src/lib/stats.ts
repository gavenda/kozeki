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

/** The currency most of [purchases] were made in, which is the one their chart is drawn in. */
function mainCurrency(purchases: Book[]): string | null {
	const counts = new Map<string, number>();
	for (const book of purchases) counts.set(book.purchaseCurrency ?? '', (counts.get(book.purchaseCurrency ?? '') ?? 0) + 1);
	let best: string | null = null;
	for (const [currency, count] of counts) if (best == null || count > counts.get(best)!) best = currency;
	return best;
}

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
	/** Monthly spending in [spendingCurrency], the currency most purchases used. */
	spendingPerMonth: number[];
	spendingCurrency: string | null;
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
		spendingPerMonth: months.map((month) =>
			sum(
				purchases
					.filter((book) => (book.purchaseCurrency ?? '') === mainCurrency(purchases) && monthOfEpochDay(book.purchasedOn!) === month)
					.map((book) => book.purchasePriceMinor!),
			),
		),
		spendingCurrency: mainCurrency(purchases),
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
	/** Yearly spending in [spendingCurrency], from the year of its first purchase to the current one. */
	spendingPerYear: { year: number; minor: number }[];
	spendingCurrency: string | null;
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
	const purchases = input.books.filter((book) => alive(book) && book.purchasedOn != null && book.purchasePriceMinor != null);
	const currency = mainCurrency(purchases);
	const charted = purchases.filter((book) => (book.purchaseCurrency ?? '') === currency);

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
		spending: spending(purchases),
		spendingPerYear: yearSpan(
			charted.map((book) => yearOfEpochDay(book.purchasedOn!)),
			yearOfEpochDay(today),
		).map((year) => ({
			year,
			minor: sum(charted.filter((book) => yearOfEpochDay(book.purchasedOn!) === year).map((book) => book.purchasePriceMinor!)),
		})),
		spendingCurrency: currency,
	};
}

/** Every year from the first of [years] to [current], or none when there is none to start from. */
function yearSpan(years: number[], current: number): number[] {
	if (years.length === 0) return [];
	const first = Math.min(...years);
	const last = Math.max(...years, current);
	return Array.from({ length: last - first + 1 }, (_, index) => first + index);
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

// ---- Longest books ---------------------------------------------------------------------------

export type LongestKind = 'PHYSICAL' | 'EPUB';

/**
 * The longest of the [completed] books with their length, longest first, of one [kind]: the
 * physical ones, which are the books without an EPUB, or the ones with one. A physical copy is as
 * long as the user says it is; an EPUB without a page count goes by its positions. A book finished
 * more than once is listed once, and one whose length is not known not at all.
 */
export function longestFinished(completed: CompletedBook[], kind: LongestKind, limit = 8): { book: Book; length: number }[] {
	const seen = new Set<string>();
	return completed
		.flatMap(({ book }) => {
			if (seen.has(book.id)) return [];
			seen.add(book.id);
			if ((kind === 'EPUB') !== inLibrary(book)) return [];
			const length = kind === 'PHYSICAL' ? (book.physicalPageCount ?? book.pageCount) : (book.pageCount ?? book.positionCount);
			return length && length > 0 ? [{ book, length }] : [];
		})
		.sort((a, b) => b.length - a.length)
		.slice(0, limit);
}

// ---- Days and periods ------------------------------------------------------------------------

export interface DayReading {
	day: number;
	durationMs: number;
	pages: number;
}

/** What was read of one book over some period. */
export interface BookReading {
	book: Book;
	durationMs: number;
	pages: number;
	startPosition?: number;
	endPosition?: number;
	/** Share of the book covered, 0 to 1, from the pages turned. */
	progressGained: number;
	/** Read in a physical copy, so untimed, its positions pages of that copy. */
	physical: boolean;
}

/** Pages per hour, or null when too little was read to say. */
export function pagesPerHour(reading: BookReading): number | null {
	return reading.durationMs >= 60_000 && reading.pages > 0 ? reading.pages / (reading.durationMs / 3_600_000) : null;
}

export type TimelineEntry =
	| { kind: 'session'; book: Book; at: number; durationMs: number; chapter?: string }
	| { kind: 'pages'; book: Book; at: number; startPage: number; endPage: number };

function perDay(sessions: ReadingSession[], paper: PhysicalReading[], start: number, endExclusive: number): DayReading[] {
	return Array.from({ length: endExclusive - start }, (_, index) => {
		const day = start + index;
		const read = sessions.filter((row) => row.day === day);
		return {
			day,
			durationMs: sum(read.map((row) => row.durationMs)),
			pages: sum(read.map((row) => row.pages ?? 0)) + sum(paper.filter((row) => row.day === day).map(pagesOf)),
		};
	});
}

/** What was read of each book: in the reader, most time first, then on paper, most pages first. */
function perBook(sessions: ReadingSession[], paper: PhysicalReading[], byId: Map<string, Book>): BookReading[] {
	const group = <T extends { bookId: string }>(rows: T[]) => {
		const out = new Map<string, T[]>();
		for (const row of rows) (out.get(row.bookId) ?? out.set(row.bookId, []).get(row.bookId)!).push(row);
		return out;
	};
	const read = [...group(sessions)].flatMap(([bookId, rows]) => {
		const book = byId.get(bookId);
		if (!book) return [];
		const ordered = [...rows].sort((a, b) => a.startedAt - b.startedAt);
		const pages = sum(ordered.map((row) => row.pages ?? 0));
		const ends = ordered.flatMap((row) => (row.endPosition != null ? [row.endPosition] : []));
		return [
			{
				book,
				durationMs: sum(ordered.map((row) => row.durationMs)),
				pages,
				startPosition: ordered.find((row) => row.startPosition != null)?.startPosition,
				endPosition: ends.length ? Math.max(...ends) : undefined,
				progressGained: book.positionCount && book.positionCount > 0 ? pages / book.positionCount : 0,
				physical: false,
			},
		];
	});
	const onPaper = [...group(paper)].flatMap(([bookId, rows]) => {
		const book = byId.get(bookId);
		if (!book) return [];
		return [
			{
				book,
				durationMs: 0,
				pages: sum(rows.map(pagesOf)),
				startPosition: Math.min(...rows.map((row) => row.startPage)),
				endPosition: Math.max(...rows.map((row) => row.endPage)),
				progressGained: 0,
				physical: true,
			},
		];
	});
	return [...read.sort((a, b) => b.durationMs - a.durationMs), ...onPaper.sort((a, b) => b.pages - a.pages)];
}

export interface DayStats {
	day: number;
	goalMinutes: number;
	durationMs: number;
	pages: number;
	books: BookReading[];
	timeline: TimelineEntry[];
	completed: CompletedBook[];
	/** The week around [day], for the day strip. */
	week: DayReading[];
}

/** The week [day] is in, starting on [firstDayOfWeek] (0 Sunday … 6 Saturday, 7 also Sunday). */
export function weekStartOf(day: number, firstDayOfWeek: number): number {
	// Epoch day 0, 1 January 1970, was a Thursday.
	const weekday = (((day + 4) % 7) + 7) % 7;
	return day - ((weekday - (firstDayOfWeek % 7) + 7) % 7);
}

export function dayStats(input: StatsInput, day: number, firstDayOfWeek: number, goalMinutes: number): DayStats {
	const { byId, sessions, paper, completed } = prepare(input);
	const weekStart = weekStartOf(day, firstDayOfWeek);
	const today = sessions.filter((row) => row.day === day);
	// The timeline shows every page entered, the totals only what counts for a book without an EPUB.
	const entered = input.physical.filter((row) => alive(row) && row.day === day && byId.has(row.bookId));
	const todayPaper = paper.filter((row) => row.day === day);
	const timeline: TimelineEntry[] = [
		...today.map((row) => ({
			kind: 'session' as const,
			book: byId.get(row.bookId)!,
			at: row.startedAt,
			durationMs: row.durationMs,
			chapter: row.endChapter ?? row.startChapter,
		})),
		...entered.map((row) => ({
			kind: 'pages' as const,
			book: byId.get(row.bookId)!,
			at: row.recordedAt,
			startPage: row.startPage,
			endPage: row.endPage,
		})),
	].sort((a, b) => a.at - b.at);
	return {
		day,
		goalMinutes,
		durationMs: sum(today.map((row) => row.durationMs)),
		pages: sum(today.map((row) => row.pages ?? 0)) + sum(todayPaper.map(pagesOf)),
		books: perBook(today, todayPaper, byId),
		timeline,
		completed: completed.filter((entry) => entry.finishedOn === day),
		week: perDay(sessions, paper, weekStart, weekStart + 7),
	};
}

export interface PeriodStats {
	start: number;
	endInclusive: number;
	goalMinutes: number;
	days: DayReading[];
	durationMs: number;
	pages: number;
	books: BookReading[];
	completed: CompletedBook[];
	/** A day read on paper took no time on record, so its pages tell it was read on. */
	daysRead: number;
	goalDaysMet: number;
}

/** A week or a month: the days from [start] to [endInclusive]. */
export function periodStats(input: StatsInput, start: number, endInclusive: number, goalMinutes: number): PeriodStats {
	const { byId, sessions, paper, completed } = prepare(input);
	const within = (day: number) => day >= start && day <= endInclusive;
	const known = sessions.filter((row) => within(row.day));
	const onPaper = paper.filter((row) => within(row.day));
	const days = perDay(known, onPaper, start, endInclusive + 1);
	return {
		start,
		endInclusive,
		goalMinutes,
		days,
		durationMs: sum(known.map((row) => row.durationMs)),
		pages: sum(known.map((row) => row.pages ?? 0)) + sum(onPaper.map(pagesOf)),
		books: perBook(known, onPaper, byId),
		completed: completed.filter((entry) => within(entry.finishedOn)),
		daysRead: days.filter((day) => day.durationMs > 0 || day.pages > 0).length,
		goalDaysMet: days.filter((day) => goalMinutes > 0 && day.durationMs >= goalMinutes * 60_000).length,
	};
}
