<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import EmptyState from '@/components/EmptyState.vue';
import { alive } from '@/lib/library';
import { formatPercent } from '@/lib/format';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Book, ReadingState } from '@/types';

const library = useLibrary();

type Filter =
	{ kind: 'all' } | { kind: 'state'; state: ReadingState } | { kind: 'wishlist' } | { kind: 'favorites' } | { kind: 'shelf'; id: string };

const STATES: ReadingState[] = ['READING', 'PLANNED', 'PAUSED', 'COMPLETED', 'DROPPED'];

const query = ref('');
const filter = ref<Filter>({ kind: 'all' });

function same(a: Filter, b: Filter): boolean {
	return JSON.stringify(a) === JSON.stringify(b);
}

const shelfMembers = computed(() => {
	const members = new Map<string, Set<string>>();
	for (const row of library.tables.shelfBooks.values()) {
		if (!alive(row)) continue;
		(members.get(row.shelfId) ?? members.set(row.shelfId, new Set()).get(row.shelfId)!).add(row.bookId);
	}
	return members;
});

function matches(book: Book, text: string): boolean {
	if (!text) return true;
	const haystack = [book.title, book.subtitle, book.series, ...(book.authors ?? []), book.isbn13].filter(Boolean).join(' ').toLowerCase();
	return text
		.toLowerCase()
		.split(/\s+/)
		.every((word) => haystack.includes(word));
}

function passes(book: Book, current: Filter): boolean {
	const wished = book.acquisition === 'WISHLIST';
	switch (current.kind) {
		case 'all':
			return !wished;
		case 'state':
			return !wished && (book.state ?? 'PLANNED') === current.state;
		case 'wishlist':
			return wished;
		case 'favorites':
			return !!book.isFavorite;
		case 'shelf':
			return shelfMembers.value.get(current.id)?.has(book.id) ?? false;
	}
}

// Most recently read first, then most recently changed, as the phone's default order.
const shown = computed(() =>
	library.books
		.filter((book) => passes(book, filter.value) && matches(book, query.value.trim()))
		.sort((a, b) => (b.lastReadAt ?? 0) - (a.lastReadAt ?? 0) || b.sync.updatedAt - a.sync.updatedAt),
);

const chips = computed<{ label: string; filter: Filter }[]>(() => [
	{ label: s.library.all, filter: { kind: 'all' } },
	...STATES.map((state) => ({ label: s.state[state], filter: { kind: 'state', state } as Filter })),
	{ label: s.library.wishlist, filter: { kind: 'wishlist' } },
	{ label: s.library.favorites, filter: { kind: 'favorites' } },
	...library.shelves.map((shelf) => ({ label: shelf.name, filter: { kind: 'shelf', id: shelf.id } as Filter })),
]);
</script>

<template>
	<div class="flex flex-col gap-4">
		<div class="flex items-center gap-2">
			<label class="flex h-12 flex-1 items-center gap-3 rounded-full bg-surface-container-high px-4 text-on-surface-variant">
				<AppIcon name="search" />
				<input v-model="query" type="search" :placeholder="s.library.search" class="w-full bg-transparent text-on-surface outline-none" />
			</label>
			<RouterLink :to="{ name: 'add' }" class="btn-filled h-12" :aria-label="s.library.add">
				<AppIcon name="add" />
				<span class="hidden sm:inline">{{ s.library.add }}</span>
			</RouterLink>
		</div>

		<div class="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 md:mx-0 md:flex-wrap md:px-0">
			<button
				v-for="chip in chips"
				:key="JSON.stringify(chip.filter)"
				class="chip shrink-0"
				:class="{ 'chip-on': same(chip.filter, filter) }"
				@click="filter = chip.filter"
			>
				<AppIcon v-if="same(chip.filter, filter)" name="check" :size="18" />
				{{ chip.label }}
			</button>
		</div>

		<EmptyState v-if="library.books.length === 0" face="happy" :title="s.library.emptyTitle" :message="s.library.emptyMessage">
			<RouterLink :to="{ name: 'add' }" class="btn-tonal mt-2">{{ s.library.add }}</RouterLink>
		</EmptyState>
		<EmptyState
			v-else-if="shown.length === 0"
			:face="query ? 'downcast' : 'neutral'"
			:title="s.library.noResultsTitle"
			:message="query ? s.library.noResults(query) : undefined"
		/>

		<ul v-else class="grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] gap-x-4 gap-y-6">
			<li v-for="book in shown" :key="book.id">
				<RouterLink :to="{ name: 'book', params: { id: book.id } }" class="group block">
					<div class="relative transition-transform group-hover:-translate-y-0.5">
						<BookCover :book="book" />
						<AppIcon v-if="book.isFavorite" name="favoriteFill" :size="20" class="absolute top-2 right-2 text-error drop-shadow" />
						<div v-if="book.state === 'READING'" class="absolute inset-x-2 bottom-2 h-1 overflow-hidden rounded-full bg-scrim/30">
							<div class="h-full rounded-full bg-primary-container" :style="{ width: formatPercent(book.progression ?? 0) }" />
						</div>
					</div>
					<p class="mt-2 line-clamp-2 text-sm font-medium">{{ book.title }}</p>
					<p class="line-clamp-1 text-xs text-on-surface-variant">{{ (book.authors ?? []).join(', ') }}</p>
				</RouterLink>
			</li>
		</ul>
	</div>
</template>
