<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import BookHeader from '@/components/book/BookHeader.vue';
import ReviewsSection from '@/components/book/ReviewsSection.vue';
import SynopsisSection from '@/components/book/SynopsisSection.vue';
import { webLink } from '@/lib/bookUi';
import { lookupMessage } from '@/lib/catalogue';
import { hardcoverSignedIn } from '@/lib/hardcover';
import * as ops from '@/lib/library';
import { useFound } from '@/stores/found';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Acquisition, MetadataSource } from '@/types';

/**
 * A book found in a catalogue that is not the user's yet, as the phone's `FoundBookScreen`: its
 * page without what only a book of one's own has, and the two ways to add it. Once added, its own
 * page takes the place of this one.
 */
const props = defineProps<{ source: string; sourceId: string }>();
const found = useFound();
const library = useLibrary();
const router = useRouter();
const source = computed(() => props.source as MetadataSource);

const book = ref<ops.Found | null>(null);
const error = ref<string | null>(null);
const missing = ref(false);

// One the user has already is shown as their own.
const owned = computed(() => library.books.find((row) => row.source === source.value && row.sourceId === props.sourceId));
watch(
	owned,
	(row) => {
		if (row) void router.replace({ name: 'book', params: { id: row.id } });
	},
	{ immediate: true },
);

async function load() {
	error.value = null;
	missing.value = false;
	book.value = found.get(source.value, props.sourceId) ?? null;
	if (book.value) return;
	try {
		book.value = await found.fetch(source.value, props.sourceId);
		missing.value = book.value == null;
	} catch (e) {
		error.value = lookupMessage(e);
	}
}
watch(() => [props.source, props.sourceId], load, { immediate: true });

function add(acquisition: Acquisition) {
	const id = library.edit((tx) => ops.addFound(tx, book.value!, acquisition));
	void router.replace({ name: 'book', params: { id } });
}
const reviewable = computed(() => source.value === 'HARDCOVER' && hardcoverSignedIn());
</script>

<template>
	<TopAppBar :title="book?.title ?? ''" :back="{ name: 'add' }" :title-after="160">
		<a
			v-if="webLink(book?.infoUrl)"
			:href="webLink(book?.infoUrl)"
			target="_blank"
			rel="noopener"
			class="icon-btn"
			:aria-label="s.book.viewInBrowser"
			:title="s.book.viewInBrowser"
		>
			<AppIcon name="openInNew" />
		</a>
	</TopAppBar>
	<div v-if="!book && !error && !missing" class="flex justify-center py-24"><HaloMark loading /></div>
	<EmptyState v-else-if="error" face="dismayed" icon="errorFill" :title="s.add.errorTitle" :message="error">
		<button class="btn-filled" @click="load">{{ s.action.retry }}</button>
	</EmptyState>
	<EmptyState v-else-if="missing" face="downcast" icon="searchOff" :title="s.add.noResultsTitle" />
	<article v-else-if="book" class="mx-auto flex max-w-[720px] flex-col gap-5 px-4 pt-2 pb-8">
		<BookHeader :book="book" />
		<div class="flex gap-2">
			<button class="btn-filled flex-1" @click="add('WISHLIST')"><AppIcon name="kidStar" :size="18" />{{ s.add.toWishlist }}</button>
			<button class="btn-tonal flex-1" @click="add('PURCHASED')">
				<AppIcon name="purchasedBookFill" :size="18" />{{ s.add.asPurchased }}
			</button>
		</div>
		<SynopsisSection :book="book" />
		<ReviewsSection v-if="reviewable" :source-id="book.sourceId" />
	</article>
</template>
