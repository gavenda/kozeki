import type { IconName } from '@/lib/icons';
import { s } from '@/strings';
import type { Book, ReadingState } from '@/types';
import { inLibrary } from './library';

/**
 * What a book shows of itself wherever it is listed, as the phone's `ReadingStateUi.kt`,
 * `OwnedBadge.kt` and the helpers on its `Book`.
 */

/** The order states are offered in: the path a book usually takes, then the ways it can stall. */
export const READING_STATE_ORDER: ReadingState[] = ['PLANNED', 'READING', 'COMPLETED', 'PAUSED', 'DROPPED'];

export const STATE_ICON: Record<ReadingState, IconName> = {
	PLANNED: 'scheduleFill',
	READING: 'menuBookFill',
	COMPLETED: 'checkCircleFill',
	DROPPED: 'stopCircleFill',
	PAUSED: 'pauseCircleFill',
};

/** The state as a bare glyph, without the disc around it, light enough to lie over a cover. */
export const STATE_GLYPH: Record<ReadingState, IconName> = {
	PLANNED: 'schedule',
	READING: 'menuBook',
	COMPLETED: 'doneAll',
	DROPPED: 'stopFill',
	PAUSED: 'pauseFill',
};

/** Wishlisted, purchased, or an EPUB: the icon and its label. */
export function ownership(book: Book): { icon: IconName; label: string } {
	switch (book.acquisition) {
		case 'PURCHASED':
			return { icon: 'purchasedBookFill', label: s.purchase.purchased };
		case 'DOWNLOADED':
			return { icon: 'libraryBooksFill', label: s.purchase.importedEpub };
		default:
			return { icon: 'kidStar', label: s.purchase.wishlist };
	}
}

export const stateOf = (book: Book): ReadingState => book.state ?? 'PLANNED';

/** Only a book the user has can be a favorite, or change state; one merely wanted is always Planned. */
export const canFavorite = (book: Book) => book.acquisition !== 'WISHLIST';
export const canChangeState = canFavorite;
export const canTrackPhysical = canFavorite;

/** The physical copy's length: its own page count, or the book's. */
export const physicalPageCount = (book: Book): number | undefined =>
	book.physicalPageCount && book.physicalPageCount > 0
		? book.physicalPageCount
		: book.pageCount && book.pageCount > 0
			? book.pageCount
			: undefined;

/** 0 to 1 through the physical copy, or null while the page or the page count is unknown. */
export function physicalProgression(book: Book): number | null {
	const count = physicalPageCount(book);
	if (book.physicalPage == null || !count) return null;
	return Math.min(Math.max(book.physicalPage / count, 0), 1);
}

/** One figure for where there is room for only one: whichever of the EPUB and the paper copy is further along. */
export function overallProgression(book: Book): number {
	if (book.state === 'COMPLETED') return 1;
	if (inLibrary(book)) return Math.max(book.progression ?? 0, physicalProgression(book) ?? 0);
	return physicalProgression(book) ?? 0;
}

/** Only a web address is a link: a synced row could hold anything, `javascript:` included. */
export const webLink = (url: string | undefined): string | undefined => (url && /^https?:\/\//i.test(url) ? url : undefined);
