import { defineStore } from 'pinia';
import { reactive } from 'vue';
import { providerFor } from '@/lib/catalogue';
import type { Found } from '@/lib/library';
import type { MetadataSource } from '@/types';

const keyOf = (source: MetadataSource, sourceId: string) => `${source}|${sourceId}`;

/**
 * Books met in the catalogues and not added: what search results, lists, authors and series
 * pages showed, so the page of one opens at once. A page opened afresh, with nothing kept, asks
 * the catalogue for the book again. Nothing here is the user's; it lives as long as the tab.
 */
export const useFound = defineStore('found', () => {
	const books = reactive(new Map<string, Found>());

	function remember(found: Found[]): void {
		for (const book of found) books.set(keyOf(book.source, book.sourceId), book);
	}

	function get(source: MetadataSource, sourceId: string): Found | undefined {
		return books.get(keyOf(source, sourceId));
	}

	async function fetch(source: MetadataSource, sourceId: string): Promise<Found | null> {
		const found = await providerFor(source).book(sourceId);
		if (found) remember([found]);
		return found;
	}

	return { remember, get, fetch };
});
