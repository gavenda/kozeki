<script setup lang="ts">
import { useIntervalFn, useWindowScroll } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import Portrait from '@/components/Portrait.vue';
import SettingsButton from '@/components/SettingsButton.vue';
import SpeechBubble from '@/components/SpeechBubble.vue';
import DeleteBooksDialog from '@/components/book/DeleteBooksDialog.vue';
import PurchaseDialog from '@/components/book/PurchaseDialog.vue';
import AddBooksMenu from '@/components/library/AddBooksMenu.vue';
import GroupHeader from '@/components/library/GroupHeader.vue';
import LibraryBookRow from '@/components/library/LibraryBookRow.vue';
import LibraryControls from '@/components/library/LibraryControls.vue';
import LibraryCoverItem from '@/components/library/LibraryCoverItem.vue';
import LibrarySearch from '@/components/library/LibrarySearch.vue';
import SelectionToolbar from '@/components/library/SelectionToolbar.vue';
import ShelfAddBooksDialog from '@/components/library/ShelfAddBooksDialog.vue';
import ShelfChecklistDialog from '@/components/library/ShelfChecklistDialog.vue';
import ShelfNameDialog from '@/components/library/ShelfNameDialog.vue';
import { canChangeState, canFavorite } from '@/lib/bookUi';
import * as ops from '@/lib/library';
import {
	filterCounts,
	filterMatches,
	LIBRARY_SORTS,
	librarySections,
	recentOrder,
	seriesGroups,
	shelfGroups,
	sortBooks,
	type BookDisplay,
	type LibraryFilter,
	type LibraryKind,
	type LibrarySection,
	type LibrarySort,
} from '@/lib/libraryView';
import { oneOf, persisted } from '@/lib/persisted';
import { shelfSummary, sortDetail } from '@/lib/sortDetail';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Book, ReadingState, Shelf } from '@/types';

const library = useLibrary();
const router = useRouter();
// How long ago a book was read is written out, so it is brought up to date every minute.
const now = ref(Date.now());
useIntervalFn(() => (now.value = Date.now()), 60_000);
const { y } = useWindowScroll();

// As on the phone: the filter starts at All each time, the rest is kept per device.
const filter = ref<LibraryFilter>('ALL');
const filtersOpen = ref(false);
const kind = persisted<LibraryKind>('library.kind', 'BOOKS', oneOf(['BOOKS', 'SHELVES'] as const));
const sort = persisted<LibrarySort>('library.sort', 'RECENT', oneOf(LIBRARY_SORTS));
const reversed = persisted<boolean>('library.sortReversed', false, (value): value is boolean => typeof value === 'boolean');
const display = persisted<BookDisplay>('library.display', 'COVERS', oneOf(['COVERS', 'LIST'] as const));
const collapsed = persisted<string[]>('library.collapsed', [], (value): value is string[] => Array.isArray(value));

const members = computed(() => library.members);
const inShelves = computed(() => kind.value === 'SHELVES');
const bySeries = computed(() => !inShelves.value && sort.value === 'SERIES');

const allBooks = computed(() => recentOrder(library.books));
// The shelves show the books that are on one, however many.
const books = computed(() => {
	if (!inShelves.value) return allBooks.value;
	const shelved = new Set([...members.value.values()].flatMap((ids) => [...ids]));
	return allBooks.value.filter((book) => shelved.has(book.id));
});
const counts = computed(() => filterCounts(books.value));
const shown = computed(() =>
	sortBooks(
		sort.value,
		books.value.filter((book) => filterMatches(filter.value, book)),
		reversed.value,
	),
);

interface Group {
	key: string;
	title: string;
	books: Book[];
	sections: LibrarySection[];
	shelf?: Shelf;
}
const groups = computed<Group[] | null>(() => {
	if (inShelves.value) {
		return shelfGroups(library.shelves, members.value, shown.value, filter.value).map((group) => ({ ...group, title: group.shelf.name }));
	}
	if (bySeries.value)
		return seriesGroups(filter.value, shown.value).map((group) => ({ ...group, title: group.name ?? s.library.noSeries }));
	return null;
});
const sections = computed(() => (groups.value ? [] : librarySections(filter.value, shown.value)));
const isCollapsed = (key: string) => collapsed.value.includes(key);
function toggleGroup(key: string) {
	collapsed.value = isCollapsed(key) ? collapsed.value.filter((entry) => entry !== key) : [...collapsed.value, key];
}

// ---- Picking books ---------------------------------------------------------------------------

const selectedIds = ref(new Set<string>());
// Only what is on screen: a book that left the filter is no longer picked.
const selection = computed(() => {
	return shown.value.filter((book) => selectedIds.value.has(book.id));
});
const selecting = computed(() => selection.value.length > 0);
const unfolded = computed(() =>
	groups.value ? groups.value.filter((group) => !isCollapsed(group.key)).flatMap((group) => group.books) : shown.value,
);
const allSelected = computed(() => unfolded.value.every((book) => selectedIds.value.has(book.id)));

function toggle(book: Book) {
	const next = new Set(selectedIds.value);
	if (next.has(book.id)) next.delete(book.id);
	else next.add(book.id);
	selectedIds.value = next;
}
const clearSelection = () => (selectedIds.value = new Set());
const selectAll = () => (selectedIds.value = new Set(unfolded.value.map((book) => book.id)));
// As on the phone, another filter or kind starts the picking over.
watch([filter, kind], clearSelection);
const open = (book: Book) => router.push({ name: 'book', params: { id: book.id } });

function setFavorite(favorite: boolean) {
	library.edit((tx) => selection.value.filter(canFavorite).forEach((book) => ops.setFavorite(tx, book.id, favorite)));
}
function setState(state: ReadingState) {
	library.edit((tx) => selection.value.filter(canChangeState).forEach((book) => ops.setState(tx, book.id, state)));
}
const editingPurchase = ref<Book | null>(null);
function markPurchased() {
	if (selection.value.length === 1) {
		editingPurchase.value = selection.value[0];
		return;
	}
	library.edit((tx) =>
		selection.value
			.filter((book) => book.acquisition !== 'PURCHASED')
			.forEach((book) => ops.setPurchase(tx, book.id, { acquisition: 'PURCHASED', ...ops.NO_PURCHASE })),
	);
}
function savePurchase(purchase: ops.Purchase) {
	const id = editingPurchase.value!.id;
	library.edit((tx) => ops.recordPurchase(tx, id, purchase));
}
function unmarkPurchased() {
	library.edit((tx) =>
		selection.value
			.filter((book) => book.acquisition === 'PURCHASED')
			.forEach((book) => ops.setPurchase(tx, book.id, { acquisition: 'WISHLIST', ...ops.NO_PURCHASE })),
	);
}
const deleting = ref(false);
function removeSelected() {
	const ids = selection.value.map((book) => book.id);
	library.edit((tx) => ids.forEach((id) => ops.deleteBook(tx, id)));
	clearSelection();
}

// ---- Shelves ---------------------------------------------------------------------------------

type ShelfPrompt = { kind: 'create' } | { kind: 'rename'; shelf: Shelf } | { kind: 'delete'; shelf: Shelf };
const shelfPrompt = ref<ShelfPrompt | null>(null);
const addingTo = ref<Shelf | null>(null);
const checklist = ref<Map<string, number> | null>(null);

function saveShelfName(name: string) {
	const prompt = shelfPrompt.value!;
	library.edit((tx) => (prompt.kind === 'rename' ? ops.renameShelf(tx, prompt.shelf.id, name) : ops.createShelf(tx, name)));
	shelfPrompt.value = null;
}
const shelfCandidates = computed(() => {
	if (!addingTo.value) return [];
	const onShelf = members.value.get(addingTo.value.id) ?? new Set();
	return library.books
		.filter((book) => !onShelf.has(book.id))
		.sort((a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: 'base' }));
});
function addToShelf(ids: string[]) {
	const shelf = addingTo.value!;
	library.edit((tx) => ids.forEach((id) => ops.setOnShelf(tx, shelf.id, id, true)));
}
const openChecklist = () => (checklist.value = library.shelfCounts(selection.value.map((book) => book.id)));
function saveChecklist(add: string[], remove: string[], create: string[]) {
	const ids = selection.value.map((book) => book.id);
	library.edit((tx) => ops.setShelves(tx, ids, add, remove, create));
}
function deleteShelf() {
	const prompt = shelfPrompt.value;
	if (prompt?.kind === 'delete') library.edit((tx) => ops.deleteShelf(tx, prompt.shelf.id));
}

// Picking the sort that is chosen turns it round; another starts in its own direction.
function pickSort(next: LibrarySort) {
	if (next === sort.value) reversed.value = !reversed.value;
	else {
		sort.value = next;
		reversed.value = false;
	}
}

const isLibraryEmpty = computed(() => library.books.length === 0);
const isShelvesEmpty = computed(() => inShelves.value && library.shelves.length === 0);
const isFilterEmpty = computed(() => !isShelvesEmpty.value && (groups.value ? groups.value.length === 0 : shown.value.length === 0));

const gridClass = computed(() => (display.value === 'COVERS' ? 'cover-grid' : 'list-grid'));
const detail = (book: Book) => sortDetail(book, sort.value, now.value);
</script>

<template>
	<div class="pb-28 [--library-header:72px]">
		<header class="px-4 pt-6 pb-3 md:pt-10">
			<h1 class="type-display-small-em">{{ s.nav.library }}</h1>
			<p class="mt-1 type-title-medium text-on-surface-variant">{{ s.bookCount(library.books.length) }}</p>
		</header>
		<div
			class="sticky top-0 z-30 flex h-[72px] items-center gap-2 px-4 transition-colors duration-200"
			:class="y > 80 ? 'bg-surface-container' : 'bg-surface'"
		>
			<LibrarySearch :books="allBooks" class="flex-1" />
			<SettingsButton />
		</div>

		<EmptyState v-if="isLibraryEmpty" face="happy" icon="shelvesFill" :title="s.library.emptyTitle" :message="s.library.emptyMessage">
			<div class="mt-2 flex flex-wrap justify-center gap-2">
				<RouterLink :to="{ name: 'add' }" class="btn-filled"><AppIcon name="travelExplore" :size="18" />{{ s.library.addFind }}</RouterLink>
				<RouterLink :to="{ name: 'addManual' }" class="btn-outlined"
					><AppIcon name="editNoteFill" :size="18" />{{ s.library.addManual }}</RouterLink
				>
			</div>
		</EmptyState>

		<template v-else>
			<LibraryControls
				v-model:filters-open="filtersOpen"
				:filter="filter"
				:counts="counts"
				:kind="kind"
				:display="display"
				:sort="sort"
				:reversed="reversed"
				class="pt-2 pb-2"
				@update:filter="filter = $event"
				@update:kind="kind = $event"
				@update:display="display = $event"
				@sort="pickSort"
			/>

			<EmptyState v-if="isShelvesEmpty" face="neutral" icon="shelvesFill" :title="s.shelves.noneTitle" :message="s.shelves.noneMessage">
				<button class="btn-filled mt-2" @click="shelfPrompt = { kind: 'create' }">
					<AppIcon name="add" :size="18" />{{ s.shelves.new }}
				</button>
			</EmptyState>
			<EmptyState
				v-else-if="isFilterEmpty"
				face="neutral"
				icon="filterAltOffFill"
				:title="s.library.filterEmptyTitle"
				:message="s.library.filterEmptyMessage"
			/>

			<template v-else>
				<template v-if="groups">
					<section v-for="group in groups" :key="group.key" class="pb-2">
						<GroupHeader
							:title="group.title"
							:count="group.books.length"
							:summary="group.shelf ? shelfSummary(group.books, sort) : null"
							:collapsed="isCollapsed(group.key)"
							class="mx-2"
							@toggle="toggleGroup(group.key)"
						>
							<template v-if="group.shelf" #actions>
								<DropdownMenu @click.stop>
									<template #trigger="{ toggle: menu }">
										<button class="icon-btn" :aria-label="s.shelves.moreActions" :title="s.shelves.moreActions" @click.stop="menu">
											<AppIcon name="moreVert" />
										</button>
									</template>
									<template #default="{ close }">
										<button class="menu-item" @click.stop="((addingTo = group.shelf!), close())">
											<AppIcon name="add" />{{ s.shelves.addBooks }}
										</button>
										<button class="menu-item" @click.stop="((shelfPrompt = { kind: 'rename', shelf: group.shelf! }), close())">
											<AppIcon name="editNoteFill" />{{ s.shelves.rename }}
										</button>
										<button class="menu-item" @click.stop="((shelfPrompt = { kind: 'delete', shelf: group.shelf! }), close())">
											<AppIcon name="deleteFill" />{{ s.shelves.delete }}
										</button>
									</template>
								</DropdownMenu>
							</template>
						</GroupHeader>
						<template v-if="!isCollapsed(group.key)">
							<div v-if="group.shelf && group.books.length === 0" class="flex flex-wrap items-center gap-2 px-4 py-3">
								<p class="type-body-medium text-on-surface-variant">{{ s.shelves.emptyTitle }}</p>
								<button class="btn-text" @click="addingTo = group.shelf!">{{ s.shelves.addBooks }}</button>
							</div>
							<template v-for="section in group.sections" :key="`${group.key}/${section.heading}`">
								<h2 v-if="section.heading" class="px-4 pt-3 pb-2 type-title-medium-em text-on-surface-variant">
									{{ s.library.filter[section.heading] }}
								</h2>
								<div :class="[gridClass, section.heading ? '' : 'pt-3']">
									<template v-for="book in section.books" :key="book.id">
										<component
											:is="display === 'COVERS' ? LibraryCoverItem : LibraryBookRow"
											:book="book"
											:detail="detail(book)"
											:selected="selectedIds.has(book.id)"
											:selecting="selecting"
											@open="open(book)"
											@toggle="toggle(book)"
										/>
									</template>
								</div>
							</template>
						</template>
					</section>
				</template>

				<template v-for="section in sections" v-else :key="section.heading ?? 'all'">
					<h2 v-if="section.heading" class="px-4 pt-4 pb-2 type-title-medium-em text-on-surface-variant">
						{{ s.library.filter[section.heading] }}
					</h2>
					<div :class="gridClass">
						<template v-for="book in section.books" :key="book.id">
							<component
								:is="display === 'COVERS' ? LibraryCoverItem : LibraryBookRow"
								:book="book"
								:detail="detail(book)"
								:selected="selectedIds.has(book.id)"
								:selecting="selecting"
								@open="open(book)"
								@toggle="toggle(book)"
							/>
						</template>
					</div>
				</template>

				<div class="flex items-center justify-center gap-2 px-4 pt-10">
					<Portrait face="content" :width="96" />
					<SpeechBubble class="max-w-[360px]">{{ s.library.endBubble }}</SpeechBubble>
				</div>
			</template>
		</template>

		<SelectionToolbar
			v-if="selecting"
			:books="selection"
			:all-selected="allSelected"
			@clear="clearSelection"
			@select-all="selectAll"
			@favorite="setFavorite"
			@shelves="openChecklist"
			@state="setState"
			@purchase="markPurchased"
			@unpurchase="unmarkPurchased"
			@remove="deleting = true"
		/>
		<AddBooksMenu
			v-else-if="!isLibraryEmpty"
			:shelves="inShelves"
			@find="router.push({ name: 'add' })"
			@manual="router.push({ name: 'addManual' })"
			@new-shelf="shelfPrompt = { kind: 'create' }"
		/>

		<ShelfNameDialog
			v-if="shelfPrompt && shelfPrompt.kind !== 'delete'"
			:name="shelfPrompt.kind === 'rename' ? shelfPrompt.shelf.name : undefined"
			@save="saveShelfName"
			@close="shelfPrompt = null"
		/>
		<ModalDialog v-if="shelfPrompt?.kind === 'delete'" :title="s.shelves.deleteTitle(shelfPrompt.shelf.name)" @close="shelfPrompt = null">
			<p>{{ s.shelves.deleteMessage }}</p>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
				<button class="btn-text text-error" @click="(deleteShelf(), close())">
					{{ s.shelves.delete }}
				</button>
			</template>
		</ModalDialog>
		<ShelfAddBooksDialog v-if="addingTo" :shelf="addingTo" :candidates="shelfCandidates" @add="addToShelf" @close="addingTo = null" />
		<ShelfChecklistDialog
			v-if="checklist"
			:shelves="library.shelves"
			:on-shelf="checklist"
			:count="selection.length"
			@save="saveChecklist"
			@close="checklist = null"
		/>
		<DeleteBooksDialog v-if="deleting" :count="selection.length" @confirm="removeSelected" @close="deleting = false" />
		<PurchaseDialog
			v-if="editingPurchase"
			:book="editingPurchase"
			:last-currency="library.lastCurrency"
			:locations="library.purchaseLocations"
			@save="savePurchase"
			@close="editingPurchase = null"
		/>
	</div>
</template>
