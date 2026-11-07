import { computed, ref, type Ref } from 'vue';
import { useRouter } from 'vue-router';
import { useFound } from '@/stores/found';
import { useLibrary } from '@/stores/library';
import type { Acquisition, Book } from '@/types';
import { addFound, type Found } from './library';

const keyOf = (found: Found) => `${found.source}|${found.sourceId}`;

/**
 * What a list of catalogue results needs, wherever it is shown: which of them the user has
 * already, opening one (their own page, or the found one), and picking several to add at once.
 */
export function useFoundList(books: Ref<Found[]>) {
	const library = useLibrary();
	const found = useFound();
	const router = useRouter();

	const owned = computed(() => {
		const out = new Map<string, Book>();
		for (const book of library.books) if (book.source && book.sourceId) out.set(`${book.source}|${book.sourceId}`, book);
		return out;
	});
	const ownedOf = (book: Found) => owned.value.get(keyOf(book));

	const picked = ref(new Set<string>());
	const selection = computed(() => books.value.filter((book) => picked.value.has(keyOf(book)) && !ownedOf(book)));
	const isPicked = (book: Found) => picked.value.has(keyOf(book));
	function toggle(book: Found) {
		const next = new Set(picked.value);
		if (next.has(keyOf(book))) next.delete(keyOf(book));
		else next.add(keyOf(book));
		picked.value = next;
	}
	const clear = () => (picked.value = new Set());

	function open(book: Found) {
		const mine = ownedOf(book);
		if (mine) {
			void router.push({ name: 'book', params: { id: mine.id } });
			return;
		}
		found.remember([book]);
		void router.push({ name: 'found', params: { source: book.source, sourceId: book.sourceId } });
	}

	function addSelected(acquisition: Acquisition) {
		const chosen = selection.value;
		library.edit((tx) => chosen.forEach((book) => addFound(tx, book, acquisition)));
		clear();
	}

	return { ownedOf, selection, isPicked, toggle, clear, open, addSelected };
}
