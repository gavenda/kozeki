<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { List, ListItem, Search } from 'tailwind-material3';
import BookCover from '@/components/BookCover.vue';
import BookStatusBadge from '@/components/BookStatusBadge.vue';
import { searchBooks } from '@/lib/libraryView';
import { s } from '@/strings';
import type { Book } from '@/types';

/**
 * The search over the library, as the phone's `BookSearchBar`, on Material's Search: a bar that opens
 * a view of the results grouped by where each book lives, docked under it on a wide window. It is not
 * a filter on the books underneath, so closing it leaves no query behind.
 */
const props = defineProps<{ books: Book[] }>();
const router = useRouter();
const query = ref('');
const open = ref(false);
watch(open, (now) => {
	if (!now) query.value = '';
});

const results = computed(() => searchBooks(query.value, props.books));
const groups = computed(() =>
	[
		{ title: s.nav.library, books: results.value.library },
		{ title: s.purchase.wishlist, books: results.value.wishlist },
		{ title: s.purchase.purchased, books: results.value.purchased },
	].filter((group) => group.books.length),
);

function openBook(book: Book) {
	open.value = false;
	void router.push({ name: 'book', params: { id: book.id } });
}
</script>

<template>
	<div class="mx-auto w-full max-w-[720px]">
		<Search v-model="query" v-model:open="open" :placeholder="s.search.hint" :back-label="s.action.back" :clear-label="s.search.clear">
			<div v-if="groups.length === 0" class="px-6 py-8 text-center">
				<template v-if="query.trim()">
					<p class="type-title-medium">{{ s.search.noResultsTitle }}</p>
					<p class="mt-1 type-body-medium text-on-surface-variant">{{ s.search.noResults(query.trim()) }}</p>
				</template>
				<p v-else class="type-body-medium text-on-surface-variant">{{ s.search.prompt }}</p>
			</div>
			<section v-for="group in groups" :key="group.title" class="pb-2">
				<h3 class="px-4 pt-4 pb-1 type-title-small text-primary">{{ s.search.groupCount(group.title, group.books.length) }}</h3>
				<List>
					<ListItem
						v-for="book in group.books"
						:key="book.id"
						as="button"
						:supporting-text="(book.authors ?? []).join(', ')"
						@click="openBook(book)"
					>
						<template #leading>
							<span class="block w-10"><BookCover :book="book" :show-title="false" rounded="rounded-sm" /></span>
						</template>
						{{ book.title }}
						<template #trailing><BookStatusBadge :book="book" /></template>
					</ListItem>
				</List>
			</section>
		</Search>
	</div>
</template>
