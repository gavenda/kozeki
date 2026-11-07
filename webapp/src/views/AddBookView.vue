<script lang="ts">
import { reactive } from 'vue';
import type { BrowseOrder } from '@/lib/catalogue';
import type { Found } from '@/lib/library';
import type { MetadataSource } from '@/types';

/** Kept while the tab lives, so coming back from a found book's page finds the results as they were. */
const kept = reactive({
	query: '',
	searched: '',
	source: null as MetadataSource | null,
	order: null as BrowseOrder | null,
	books: [] as Found[],
	page: 0,
	hasMore: false,
	ran: false,
});
</script>

<script setup lang="ts">
import { useIntersectionObserver } from '@vueuse/core';
import { computed, ref, toRef } from 'vue';
import AddSelectedToolbar from '@/components/AddSelectedToolbar.vue';
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import MetadataResultItem from '@/components/MetadataResultItem.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import { defaultSource, lookupMessage, providerFor } from '@/lib/catalogue';
import { useFoundList } from '@/lib/foundList';
import { hardcoverAvailable } from '@/lib/hardcover';
import { oneOf, persisted } from '@/lib/persisted';
import { useFound } from '@/stores/found';
import { s } from '@/strings';

/** Finding a book to add, as the phone's `AddBookScreen`: a search of a catalogue, or one of its lists. */
const foundStore = useFound();
kept.source ??= defaultSource();
const source = computed(() => kept.source!);
const provider = computed(() => providerFor(source.value));
const sources = computed<MetadataSource[]>(() => (hardcoverAvailable() ? ['HARDCOVER', 'GOOGLE_BOOKS'] : ['GOOGLE_BOOKS']));
const display = persisted<'LIST' | 'COVERS'>('add.display', 'LIST', oneOf(['LIST', 'COVERS'] as const));

const loading = ref(false);
const error = ref<string | null>(null);
const list = useFoundList(toRef(kept, 'books'));

async function fetchPage(page: number) {
	const p = provider.value;
	return kept.order && !kept.searched ? p.browse!(kept.order, page) : p.search(kept.searched, page);
}

async function load(page: number) {
	if (loading.value) return;
	loading.value = true;
	error.value = null;
	try {
		const result = await fetchPage(page);
		foundStore.remember(result.books);
		const seen = new Set(page === 1 ? [] : kept.books.map((book) => book.sourceId));
		kept.books = [...(page === 1 ? [] : kept.books), ...result.books.filter((book) => !seen.has(book.sourceId))];
		kept.page = page;
		kept.hasMore = result.hasMore;
		kept.ran = true;
	} catch (e) {
		error.value = lookupMessage(e);
	} finally {
		loading.value = false;
	}
}

function search() {
	const query = kept.query.trim();
	if (!query) return;
	kept.searched = query;
	kept.order = null;
	list.clear();
	void load(1);
}
function browse(order: BrowseOrder) {
	kept.order = order;
	kept.searched = '';
	kept.query = '';
	list.clear();
	void load(1);
}
function pickSource(next: MetadataSource) {
	if (next === kept.source) return;
	kept.source = next;
	kept.order = null;
	kept.books = [];
	kept.ran = false;
	error.value = null;
	list.clear();
	if (kept.searched) void load(1);
}
function clearQuery() {
	kept.query = '';
}

const sentinel = ref<HTMLElement>();
useIntersectionObserver(sentinel, ([entry]) => {
	if (entry?.isIntersecting && kept.hasMore && !loading.value && !error.value) void load(kept.page + 1);
});
const showLists = computed(() => !kept.query.trim() && provider.value.browseOrders.length > 0);
</script>

<template>
	<TopAppBar :title="s.add.title" :back="{ name: 'library' }" always-title>
		<DropdownMenu v-if="showLists">
			<template #trigger="{ toggle }">
				<button
					class="icon-btn-tonal bg-secondary-container text-on-secondary-container"
					:aria-label="s.add.lists"
					:title="s.add.lists"
					@click="toggle"
				>
					<AppIcon name="sort" />
				</button>
			</template>
			<template #default="{ close }">
				<button
					v-for="order in provider.browseOrders"
					:key="order"
					class="menu-item"
					:class="{ 'text-primary': order === kept.order }"
					@click="(browse(order), close())"
				>
					<span class="flex-1">{{ s.add.browse[order] }}</span>
					<AppIcon v-if="order === kept.order" name="check" />
				</button>
			</template>
		</DropdownMenu>
		<button
			class="icon-btn"
			:aria-label="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
			:title="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
			@click="display = display === 'COVERS' ? 'LIST' : 'COVERS'"
		>
			<AppIcon :name="display === 'COVERS' ? 'viewListFill' : 'gridViewFill'" />
		</button>
	</TopAppBar>

	<div class="mx-auto max-w-[720px] pb-28">
		<form class="px-4 py-2" @submit.prevent="search">
			<label class="flex h-14 items-center gap-2 rounded-full bg-surface-container-high ps-2 pe-2 text-on-surface-variant">
				<button class="icon-btn" :aria-label="s.action.search" type="submit"><AppIcon name="search" /></button>
				<input
					v-model="kept.query"
					type="search"
					:placeholder="s.add.search"
					class="w-full bg-transparent type-body-large text-on-surface outline-none [&::-webkit-search-cancel-button]:hidden"
					autofocus
				/>
				<button v-if="kept.query" type="button" class="icon-btn" :aria-label="s.search.clear" @click="clearQuery">
					<AppIcon name="close" />
				</button>
			</label>
		</form>
		<div class="flex gap-2 overflow-x-auto px-4 pb-2">
			<button
				v-for="option in sources"
				:key="option"
				class="chip shrink-0"
				:class="{ 'chip-on': option === source }"
				@click="pickSource(option)"
			>
				<AppIcon v-if="option === source" name="check" :size="18" />{{ s.source[option] }}
			</button>
		</div>

		<EmptyState v-if="error && kept.page === 0" face="dismayed" icon="errorFill" :title="s.add.errorTitle" :message="error">
			<button class="btn-filled" @click="load(1)">{{ s.action.retry }}</button>
		</EmptyState>
		<div v-else-if="(loading && kept.page === 0) || (loading && !kept.books.length)" class="flex justify-center py-16">
			<HaloMark loading />
		</div>
		<EmptyState v-else-if="!kept.ran" face="studious" icon="travelExplore" :title="s.add.promptTitle" :message="s.add.promptMessage" />
		<EmptyState
			v-else-if="kept.books.length === 0"
			face="worried"
			icon="searchOff"
			:title="s.add.noResultsTitle"
			:message="s.add.noResultsMessage"
		/>
		<template v-else>
			<p v-if="source === 'GOOGLE_BOOKS'" class="px-4 pb-2 type-label-small text-on-surface-variant">{{ s.add.attributionGoogle }}</p>
			<div :class="display === 'COVERS' ? 'cover-grid' : 'flex flex-col'">
				<MetadataResultItem
					v-for="book in kept.books"
					:key="book.sourceId"
					:found="book"
					:owned="list.ownedOf(book)"
					:layout="display === 'COVERS' ? 'cover' : 'row'"
					:selected="list.isPicked(book)"
					:selecting="list.selection.value.length > 0"
					@open="list.open(book)"
					@toggle="list.toggle(book)"
				/>
			</div>
			<div ref="sentinel" class="flex justify-center py-6">
				<HaloMark v-if="loading" loading :size="32" />
				<button v-else-if="error" class="btn-text" @click="load(kept.page + 1)">{{ s.action.retry }}</button>
			</div>
		</template>
	</div>
	<AddSelectedToolbar v-if="list.selection.value.length" :count="list.selection.value.length" @clear="list.clear" @add="list.addSelected" />
</template>
