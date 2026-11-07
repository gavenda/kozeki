<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AddSelectedToolbar from '@/components/AddSelectedToolbar.vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import BookStatusBadge from '@/components/BookStatusBadge.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import MetadataResultItem from '@/components/MetadataResultItem.vue';
import Portrait from '@/components/Portrait.vue';
import SpeechBubble from '@/components/SpeechBubble.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import { lookupMessage, providerFor, type SeriesPage } from '@/lib/catalogue';
import { useFoundList } from '@/lib/foundList';
import { hardcoverSignedIn } from '@/lib/hardcover';
import { normalize } from '@/lib/libraryView';
import { useFound } from '@/stores/found';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Book } from '@/types';

/**
 * A series by its name, as the phone's `SeriesScreen`: the user's books in it, by number, then the
 * rest of it on Hardcover. When every book of it is already here, Ui says the set is complete.
 */
const props = defineProps<{ name: string }>();
const library = useLibrary();
const foundStore = useFound();

const yours = computed(() =>
	library.books
		.filter((book) => book.series && normalize(book.series) === normalize(props.name))
		.sort((a, b) => (a.seriesNumber ?? Infinity) - (b.seriesNumber ?? Infinity) || a.title.localeCompare(b.title)),
);

const remote = ref<SeriesPage | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const online = hardcoverSignedIn();
async function load() {
	if (!online) return;
	loading.value = true;
	error.value = null;
	try {
		remote.value = await providerFor('HARDCOVER').series!(props.name);
		foundStore.remember(remote.value.books);
	} catch (e) {
		error.value = lookupMessage(e);
	} finally {
		loading.value = false;
	}
}
watch(() => props.name, load, { immediate: true });

const remoteBooks = computed(() => remote.value?.books ?? []);
const list = useFoundList(remoteBooks);
const more = computed(() =>
	remoteBooks.value.filter((book) => !list.ownedOf(book) && !yours.value.some((own) => own.sourceId === book.sourceId)),
);
const label = (book: Book) => (book.subtitle ? `${book.title}: ${book.subtitle}` : book.title);
</script>

<template>
	<TopAppBar :title="remote?.name ?? name" :back="{ name: 'library' }" always-title>
		<a
			v-if="remote?.infoUrl"
			:href="remote.infoUrl"
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
		<template v-if="yours.length">
			<h2 class="px-4 pt-4 pb-2 type-title-medium">{{ s.series.yourBooks }}</h2>
			<RouterLink
				v-for="book in yours"
				:key="book.id"
				:to="{ name: 'book', params: { id: book.id } }"
				class="flex items-center gap-3 rounded-xl px-4 py-2.5 hover:bg-on-surface/5"
			>
				<span class="w-12 shrink-0"><BookCover :book="book" :show-title="false" rounded="rounded-lg" /></span>
				<span class="min-w-0 flex-1">
					<span class="line-clamp-2 type-body-large">{{ label(book) }}</span>
					<span class="block truncate type-body-medium text-on-surface-variant">{{ (book.authors ?? []).join(', ') }}</span>
				</span>
				<span v-if="book.seriesNumber != null" class="type-label-large text-on-surface-variant">{{
					s.series.number(book.seriesNumber)
				}}</span>
				<BookStatusBadge :book="book" />
			</RouterLink>
		</template>

		<template v-if="online">
			<div v-if="loading" class="flex justify-center py-12"><HaloMark loading /></div>
			<EmptyState v-else-if="error" face="dismayed" icon="errorFill" :title="s.series.errorTitle" :message="error">
				<button class="btn-filled" @click="load">{{ s.action.retry }}</button>
			</EmptyState>
			<EmptyState
				v-else-if="remote && !remote.name && !yours.length"
				face="dumbfounded"
				icon="libraryBooksFill"
				:title="s.series.notFoundTitle"
				:message="s.series.notFound"
			/>
			<template v-else-if="remote?.name">
				<template v-if="more.length">
					<h2 class="px-4 pt-6 pb-2 type-title-medium">{{ yours.length ? s.series.more : s.series.books }}</h2>
					<MetadataResultItem
						v-for="book in more"
						:key="book.sourceId"
						:found="book"
						layout="row"
						:trailing="book.seriesNumber != null ? s.series.number(book.seriesNumber) : undefined"
						:selected="list.isPicked(book)"
						:selecting="list.selection.value.length > 0"
						@open="list.open(book)"
						@toggle="list.toggle(book)"
					/>
				</template>
				<div v-else class="flex items-center justify-center gap-2 px-4 pt-10">
					<Portrait face="happy" :width="96" />
					<SpeechBubble class="max-w-[360px]">{{ s.series.endBubble }}</SpeechBubble>
				</div>
			</template>
		</template>
		<EmptyState
			v-else-if="!yours.length"
			face="dumbfounded"
			icon="libraryBooksFill"
			:title="s.series.notFoundTitle"
			:message="s.lookupError.signedOut"
		/>
	</div>
	<AddSelectedToolbar v-if="list.selection.value.length" :count="list.selection.value.length" @clear="list.clear" @add="list.addSelected" />
</template>
