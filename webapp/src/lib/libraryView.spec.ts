import { describe, expect, it } from 'vitest';
import type { Book, Shelf } from '@/types';
import {
	filterCounts,
	isDescending,
	librarySections,
	priceTotals,
	recentOrder,
	searchBooks,
	seriesGroups,
	shelfGroups,
	sortBooks,
} from './libraryView';

const book = (id: string, extra: Partial<Book> = {}, created = 1): Book => ({
	id,
	title: id,
	sync: { createdAt: created, updatedAt: created },
	...extra,
});

describe('filters', () => {
	it('counts All over everything, the wishlist apart from the states', () => {
		const books = [
			book('a', { state: 'READING', acquisition: 'DOWNLOADED' }),
			book('b', { acquisition: 'WISHLIST' }),
			book('c', { acquisition: 'PURCHASED', isFavorite: true }),
		];
		const counts = filterCounts(books);
		expect(counts.ALL).toBe(3);
		expect(counts.WISHLIST).toBe(1);
		expect(counts.PLANNED).toBe(1);
		expect(counts.PURCHASED).toBe(1);
		expect(counts.FAVORITES).toBe(1);
		expect(counts.READING).toBe(1);
	});

	it('groups All by state, the wishlist after what is planned', () => {
		const books = [book('w', { acquisition: 'WISHLIST' }), book('p'), book('r', { state: 'READING' }), book('c', { state: 'COMPLETED' })];
		expect(librarySections('ALL', books).map((section) => [section.heading, section.books.map((b) => b.id)])).toEqual([
			['READING', ['r']],
			['PLANNED', ['p']],
			['WISHLIST', ['w']],
			['COMPLETED', ['c']],
		]);
		expect(librarySections('PLANNED', [book('p')])).toEqual([{ heading: null, books: [book('p')] }]);
	});
});

describe('sorting', () => {
	it('puts books with an EPUB first by when they were read, then the rest newest first', () => {
		const books = [
			book('old', {}, 1),
			book('new', {}, 5),
			book('epub', { fileHash: 'x', lastReadAt: 2 }),
			book('fresh', { fileHash: 'y' }, 9),
		];
		expect(recentOrder(books).map((b) => b.id)).toEqual(['fresh', 'epub', 'new', 'old']);
	});

	it('keeps books without a value at the end, whichever way round', () => {
		const books = [book('none'), book('low', { rating: 2 }), book('high', { rating: 5 })];
		expect(sortBooks('RATING', books).map((b) => b.id)).toEqual(['high', 'low', 'none']);
		expect(sortBooks('RATING', books, true).map((b) => b.id)).toEqual(['low', 'high', 'none']);
		expect(isDescending('RATING', false)).toBe(true);
		expect(isDescending('TITLE', true)).toBe(true);
	});

	it('keeps each currency together, the most used first', () => {
		const books = [
			book('eur', { purchasePriceMinor: 900, purchaseCurrency: 'EUR' }),
			book('usd1', { purchasePriceMinor: 100, purchaseCurrency: 'USD' }),
			book('usd2', { purchasePriceMinor: 500, purchaseCurrency: 'USD' }),
		];
		expect(sortBooks('PRICE', books).map((b) => b.id)).toEqual(['usd2', 'usd1', 'eur']);
		expect(priceTotals(books)).toEqual([
			{ currency: 'USD', minor: 600 },
			{ currency: 'EUR', minor: 900 },
		]);
	});

	it('sorts a series by number, unnumbered after, and groups it', () => {
		const books = [
			book('b2', { series: 'Dune', seriesNumber: 2 }),
			book('x', { series: 'dune' }),
			book('b1', { series: 'Dune', seriesNumber: 1 }),
			book('alone'),
		];
		const sorted = sortBooks('SERIES', books);
		expect(sorted.map((b) => b.id)).toEqual(['b1', 'b2', 'x', 'alone']);
		expect(seriesGroups('ALL', sorted).map((group) => [group.name, group.books.length])).toEqual([
			['Dune', 3],
			[null, 1],
		]);
	});
});

describe('shelves', () => {
	it('shows every shelf under All, and only the ones with books under another filter', () => {
		const shelves: Shelf[] = [
			{ id: 's1', name: 'One', sync: { createdAt: 1, updatedAt: 1 } },
			{ id: 's2', name: 'Two', sync: { createdAt: 1, updatedAt: 1 } },
		];
		const members = new Map([['s1', new Set(['a'])]]);
		const books = [book('a', { state: 'READING' })];
		expect(shelfGroups(shelves, members, books, 'ALL').map((group) => group.shelf.id)).toEqual(['s1', 's2']);
		expect(shelfGroups(shelves, members, books, 'READING').map((group) => group.shelf.id)).toEqual(['s1']);
	});
});

describe('search', () => {
	it('matches every word, ignoring case, accents and apostrophes, and groups by where a book lives', () => {
		const books = [
			book('a', { title: "Ender's Game", authors: ['Orson Scott Card'], fileHash: 'x' }),
			book('b', { title: 'Les Misérables', acquisition: 'WISHLIST' }),
			book('c', { title: 'Enders', acquisition: 'PURCHASED' }),
		];
		const found = searchBooks('enders game', books);
		expect(found.library.map((b) => b.id)).toEqual(['a']);
		expect(searchBooks('miserables', books).wishlist.map((b) => b.id)).toEqual(['b']);
		expect(searchBooks('ENDERS', books).purchased.map((b) => b.id)).toEqual(['c']);
		expect(searchBooks('!!', books).library).toEqual([]);
	});
});
