import type { SyncStamp } from '../shared/tables';

/** The rows as the server sends them, which is the Android app's Room entities. */

export type ReadingState = 'PLANNED' | 'READING' | 'COMPLETED' | 'DROPPED' | 'PAUSED';
export type Acquisition = 'WISHLIST' | 'PURCHASED' | 'DOWNLOADED';
export type ReadOutcome = 'COMPLETED' | 'DROPPED';
export type MetadataSource = 'HARDCOVER' | 'GOOGLE_BOOKS';

export interface AuthorRef {
	name: string;
	id: string;
}

export interface Book {
	id: string;
	title: string;
	subtitle?: string;
	authors?: string[];
	authorRefs?: AuthorRef[];
	description?: string;
	publisher?: string;
	series?: string;
	seriesNumber?: number;
	publishedDate?: string;
	language?: string;
	pageCount?: number;
	isbn10?: string;
	isbn13?: string;
	categories?: string[];
	coverFile?: string;
	coverUrl?: string;
	customCover?: boolean;
	detailsEdited?: boolean;
	state?: ReadingState;
	acquisition?: Acquisition;
	isFavorite?: boolean;
	rating?: number;
	purchasePriceMinor?: number;
	purchaseCurrency?: string;
	purchasedOn?: number;
	purchaseLocation?: string;
	purchaseMemo?: string;
	source?: MetadataSource;
	sourceId?: string;
	sourceUrl?: string;
	matchStatus?: string;
	fileHash?: string;
	fileSize?: number;
	originalFileName?: string;
	locator?: string;
	progression?: number;
	position?: number;
	positionCount?: number;
	chapterTitle?: string;
	chapterIndex?: number;
	chapterCount?: number;
	lastReadAt?: number;
	physicalPage?: number;
	physicalPageCount?: number;
	sync: SyncStamp;
}

export interface ReadThrough {
	id: string;
	bookId: string;
	number: number;
	startedAt: number;
	finishedAt?: number;
	outcome?: ReadOutcome;
	finishedOn?: number;
	sync: SyncStamp;
}

export interface ReadingSession {
	id: string;
	bookId: string;
	readThroughId?: string;
	startedAt: number;
	endedAt: number;
	durationMs: number;
	day: number;
	startProgression: number;
	endProgression: number;
	startPosition?: number;
	endPosition?: number;
	pages?: number;
	startChapter?: string;
	endChapter?: string;
	sync: SyncStamp;
}

export interface PhysicalReading {
	id: string;
	bookId: string;
	readThroughId?: string;
	recordedAt: number;
	day: number;
	startPage: number;
	endPage: number;
	sync: SyncStamp;
}

export interface Note {
	id: string;
	bookId: string;
	text: string;
	chapterTitle?: string;
	progression?: number;
	sync: SyncStamp;
}

export interface YearlyGoal {
	year: number;
	books: number;
	sync: SyncStamp;
}

export interface Shelf {
	id: string;
	name: string;
	sync: SyncStamp;
}

export interface ShelfBook {
	shelfId: string;
	bookId: string;
	sync: SyncStamp;
}

export interface Setting {
	key: string;
	value?: unknown;
	sync: SyncStamp;
}

export interface Rows {
	books: Book[];
	readThroughs: ReadThrough[];
	readingSessions: ReadingSession[];
	physicalReadings: PhysicalReading[];
	notes: Note[];
	yearlyGoals: YearlyGoal[];
	shelves: Shelf[];
	shelfBooks: ShelfBook[];
	settings: Setting[];
}

export type Changes = Partial<Rows>;
