import type { SortDetail } from '@/components/library/SortDetailLine.vue';
import type { Book } from '@/types';
import { formatDate, formatPrice, formatTimeAgo } from './format';
import { averageRating, priceTotals, type LibrarySort } from './libraryView';

/** The detail of [book] under [sort], or null to show its authors as usual (the phone's `sortDetail`). */
export function sortDetail(book: Book, sort: LibrarySort, now: number): SortDetail | null {
	switch (sort) {
		case 'RECENT':
			return book.lastReadAt ? { kind: 'line', text: formatTimeAgo(book.lastReadAt, now) } : null;
		case 'DATE_ADDED':
			return book.sync.createdAt > 0 ? { kind: 'line', text: formatDate(book.sync.createdAt) } : null;
		case 'RATING':
			return book.rating != null ? { kind: 'stars', rating: book.rating } : null;
		case 'PRICE':
			return book.purchasePriceMinor != null ? { kind: 'line', text: formatPrice(book.purchasePriceMinor, book.purchaseCurrency) } : null;
		default:
			return null;
	}
}

/** What a shelf of [books] says in place of its count under [sort]: the total price, or the average rating. */
export function shelfSummary(books: Book[], sort: LibrarySort): SortDetail | null {
	if (sort === 'PRICE') {
		const text = priceTotals(books)
			.map(({ currency, minor }) => formatPrice(minor, currency))
			.join(' + ');
		return text ? { kind: 'line', text } : null;
	}
	if (sort === 'RATING') {
		const average = averageRating(books);
		return average != null ? { kind: 'stars', rating: average, label: average.toFixed(1) } : null;
	}
	return null;
}
