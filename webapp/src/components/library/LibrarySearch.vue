<script setup lang="ts">
import { onClickOutside, onKeyStroke } from '@vueuse/core';
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import BookStatusBadge from '@/components/BookStatusBadge.vue';
import { searchBooks } from '@/lib/libraryView';
import { s } from '@/strings';
import type { Book } from '@/types';

/**
 * The search over the library, as the phone's `BookSearchBar` on a wide window: a pill that opens
 * a panel docked under it, the results grouped by where each book lives. It is not a filter on the
 * books underneath, so closing it leaves no query behind.
 */
const props = defineProps<{ books: Book[] }>();
const router = useRouter();
const query = ref('');
const open = ref(false);
const root = ref<HTMLElement>();
const input = ref<HTMLInputElement>();

function close() {
	open.value = false;
	query.value = '';
	input.value?.blur();
}
onClickOutside(root, () => open.value && close());
onKeyStroke('Escape', () => open.value && close());

const results = computed(() => searchBooks(query.value, props.books));
const groups = computed(() =>
	[
		{ title: s.nav.library, books: results.value.library },
		{ title: s.purchase.wishlist, books: results.value.wishlist },
		{ title: s.purchase.purchased, books: results.value.purchased },
	].filter((group) => group.books.length),
);

function openBook(book: Book) {
	close();
	void router.push({ name: 'book', params: { id: book.id } });
}
</script>

<template>
	<div ref="root" class="relative mx-auto w-full max-w-[720px]">
		<label
			class="flex h-14 items-center gap-4 bg-surface-container-high px-4 text-on-surface-variant transition-[border-radius]"
			:class="open ? 'rounded-t-[28px]' : 'rounded-full'"
		>
			<button v-if="open" type="button" class="icon-btn -ms-2" :aria-label="s.action.back" @click="close">
				<AppIcon name="arrowBack" />
			</button>
			<AppIcon v-else name="search" />
			<input
				ref="input"
				v-model="query"
				type="search"
				class="w-full bg-transparent type-body-large text-on-surface outline-none placeholder:text-on-surface-variant [&::-webkit-search-cancel-button]:hidden"
				:placeholder="s.search.hint"
				@focus="open = true"
			/>
			<button v-if="query" type="button" class="icon-btn -me-2" :aria-label="s.search.clear" @click="((query = ''), input?.focus())">
				<AppIcon name="close" />
			</button>
		</label>
		<div
			v-if="open"
			class="absolute inset-x-0 top-full z-40 max-h-[70vh] overflow-y-auto rounded-b-[28px] bg-surface-container-high pb-4 shadow-lg"
		>
			<div v-if="groups.length === 0" class="px-6 py-8 text-center">
				<template v-if="query.trim()">
					<p class="type-title-medium">{{ s.search.noResultsTitle }}</p>
					<p class="mt-1 type-body-medium text-on-surface-variant">{{ s.search.noResults(query.trim()) }}</p>
				</template>
				<p v-else class="type-body-medium text-on-surface-variant">{{ s.search.prompt }}</p>
			</div>
			<section v-for="group in groups" :key="group.title">
				<h3 class="px-4 pt-4 pb-1 type-title-small text-primary">{{ s.search.groupCount(group.title, group.books.length) }}</h3>
				<button
					v-for="book in group.books"
					:key="book.id"
					class="flex w-full cursor-pointer items-center gap-4 px-4 py-2 text-start hover:bg-on-surface/8"
					@click="openBook(book)"
				>
					<span class="w-10 shrink-0"><BookCover :book="book" :show-title="false" rounded="rounded-sm" /></span>
					<span class="min-w-0 flex-1">
						<span class="block truncate type-body-large">{{ book.title }}</span>
						<span class="block truncate type-body-medium text-on-surface-variant">{{ (book.authors ?? []).join(', ') }}</span>
					</span>
					<BookStatusBadge :book="book" />
				</button>
			</section>
		</div>
	</div>
</template>
