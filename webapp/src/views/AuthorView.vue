<script setup lang="ts">
import { useIntersectionObserver } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import AddSelectedToolbar from '@/components/AddSelectedToolbar.vue';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import ExpandableText from '@/components/ExpandableText.vue';
import HaloMark from '@/components/HaloMark.vue';
import MetadataResultItem from '@/components/MetadataResultItem.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import { lookupMessage, providerFor, type AuthorPage } from '@/lib/catalogue';
import { useFoundList } from '@/lib/foundList';
import type { Found } from '@/lib/library';
import { useFound } from '@/stores/found';
import { s } from '@/strings';

/** An author's page on Hardcover, as the phone's `AuthorScreen`: who they are, then their books, most read first. */
const props = defineProps<{ id: string }>();
const foundStore = useFound();
const author = ref<AuthorPage['author']>(null);
const books = ref<Found[]>([]);
const page = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const list = useFoundList(books);

async function load(next: number) {
	if (loading.value) return;
	loading.value = true;
	error.value = null;
	try {
		const result = await providerFor('HARDCOVER').author!(props.id, next);
		foundStore.remember(result.books.books);
		if (next === 1) author.value = result.author;
		books.value = next === 1 ? result.books.books : [...books.value, ...result.books.books];
		hasMore.value = result.books.hasMore;
		page.value = next;
	} catch (e) {
		error.value = lookupMessage(e);
	} finally {
		loading.value = false;
	}
}
watch(
	() => props.id,
	() => {
		author.value = null;
		books.value = [];
		page.value = 0;
		void load(1);
	},
	{ immediate: true },
);
const sentinel = ref<HTMLElement>();
useIntersectionObserver(sentinel, ([entry]) => {
	if (entry?.isIntersecting && hasMore.value && !loading.value && !error.value) void load(page.value + 1);
});

const details = computed(() => {
	const a = author.value;
	if (!a) return '';
	const years = a.bornYear && a.deathYear ? s.author.years(a.bornYear, a.deathYear) : a.bornYear ? s.author.born(a.bornYear) : null;
	return [years, a.location, a.booksCount ? s.bookCount(a.booksCount) : null].filter(Boolean).join(' · ');
});
</script>

<template>
	<TopAppBar :title="author?.name ?? ''" :back="{ name: 'library' }" :title-after="100">
		<a
			v-if="author?.infoUrl"
			:href="author.infoUrl"
			target="_blank"
			rel="noopener"
			class="icon-btn"
			:aria-label="s.book.viewInBrowser"
			:title="s.book.viewInBrowser"
		>
			<AppIcon name="openInNew" />
		</a>
	</TopAppBar>
	<div class="mx-auto max-w-[720px] pb-28">
		<div v-if="loading && page === 0" class="flex justify-center py-24"><HaloMark loading /></div>
		<EmptyState v-else-if="error && page === 0" face="dismayed" icon="errorFill" :title="s.author.errorTitle" :message="error">
			<button class="btn-filled" @click="load(1)">{{ s.action.retry }}</button>
		</EmptyState>
		<EmptyState v-else-if="!author" face="dumbfounded" icon="personFill" :title="s.author.notFoundTitle" :message="s.author.notFound" />
		<template v-else>
			<header class="flex flex-col gap-4 px-4 py-2">
				<div class="flex items-center gap-4">
					<img
						v-if="author.imageUrl"
						:src="author.imageUrl"
						:alt="s.author.photo(author.name)"
						class="size-22 shrink-0 rounded-full bg-surface-container-highest object-cover"
						referrerpolicy="no-referrer"
					/>
					<span
						v-else
						class="flex size-22 shrink-0 items-center justify-center rounded-full bg-surface-container-highest text-on-surface-variant"
					>
						<AppIcon name="personFill" :size="40" />
					</span>
					<div class="min-w-0">
						<h1 class="type-headline-small-em">{{ author.name }}</h1>
						<p v-if="details" class="type-body-medium text-on-surface-variant">{{ details }}</p>
					</div>
				</div>
				<ExpandableText v-if="author.bio" :text="author.bio" :lines="5" />
			</header>
			<EmptyState
				v-if="books.length === 0"
				face="downcast"
				icon="libraryBooksFill"
				:title="s.author.noBooksTitle"
				:message="s.author.noBooksMessage"
			/>
			<template v-else>
				<h2 class="px-4 pt-4 pb-2 type-title-medium">{{ s.author.books }}</h2>
				<MetadataResultItem
					v-for="book in books"
					:key="book.sourceId"
					:found="book"
					:owned="list.ownedOf(book)"
					layout="row"
					:selected="list.isPicked(book)"
					:selecting="list.selection.value.length > 0"
					@open="list.open(book)"
					@toggle="list.toggle(book)"
				/>
				<div ref="sentinel" class="flex justify-center py-6">
					<HaloMark v-if="loading" loading :size="32" />
					<button v-else-if="error" class="btn-text" @click="load(page + 1)">{{ s.action.retry }}</button>
				</div>
			</template>
		</template>
	</div>
	<AddSelectedToolbar v-if="list.selection.value.length" :count="list.selection.value.length" @clear="list.clear" @add="list.addSelected" />
</template>
