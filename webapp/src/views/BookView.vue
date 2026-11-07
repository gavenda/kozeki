<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import BookHeader from '@/components/book/BookHeader.vue';
import DeleteBooksDialog from '@/components/book/DeleteBooksDialog.vue';
import HistorySection from '@/components/book/HistorySection.vue';
import NoteDialog from '@/components/book/NoteDialog.vue';
import NotesSection from '@/components/book/NotesSection.vue';
import OwnershipSection from '@/components/book/OwnershipSection.vue';
import PhysicalCard from '@/components/book/PhysicalCard.vue';
import PhysicalDialog, { pagesUnread } from '@/components/book/PhysicalDialog.vue';
import ProgressCard from '@/components/book/ProgressCard.vue';
import PurchaseDialog from '@/components/book/PurchaseDialog.vue';
import ReviewsSection from '@/components/book/ReviewsSection.vue';
import StatusGroup from '@/components/book/StatusGroup.vue';
import SynopsisSection from '@/components/book/SynopsisSection.vue';
import ShelfChecklistDialog from '@/components/library/ShelfChecklistDialog.vue';
import { canChangeState, canFavorite, canTrackPhysical, physicalPageCount, stateOf, webLink } from '@/lib/bookUi';
import { uploadCover } from '@/lib/covers';
import { hardcoverSignedIn } from '@/lib/hardcover';
import * as ops from '@/lib/library';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Note } from '@/types';

/** A book of the user's own, as the phone's `BookDetailScreen`: one column of what is known and kept of it. */
const props = defineProps<{ id: string }>();
const library = useLibrary();
const router = useRouter();

const book = computed(() => {
	const row = library.tables.books.get(props.id);
	return row && ops.alive(row) ? row : undefined;
});
const of = <T extends { bookId: string; sync: { deletedAt?: number | null } }>(rows: Iterable<T>) =>
	[...rows].filter((row) => ops.alive(row) && row.bookId === props.id);

const notes = computed(() => of(library.tables.notes.values()).sort((a, b) => b.sync.createdAt - a.sync.createdAt));
const readThroughs = computed(() => of(library.tables.readThroughs.values()).sort((a, b) => b.number - a.number));
const readingTimeMs = computed(() => of(library.tables.readingSessions.values()).reduce((sum, row) => sum + row.durationMs, 0));
const readingNumber = computed(() => Math.max(1, ...readThroughs.value.map((row) => row.number)));
// The paper readings of the reading the book is on, which going back a page would take back.
const currentReadings = computed(() => {
	const open = readThroughs.value.find((row) => row.finishedAt == null);
	const finished = ops.FINISHED_STATES.includes(stateOf(book.value!)) ? readThroughs.value[0] : undefined;
	const current = open ?? finished;
	return of(library.tables.physicalReadings.values()).filter((row) => (row.readThroughId ?? undefined) === current?.id);
});
const reviewable = computed(() => book.value?.source === 'HARDCOVER' && !!book.value.sourceId && hardcoverSignedIn());
const sourceLink = computed(() => webLink(book.value?.sourceUrl));

const edit = library.edit;

// ---- Paper copy -----------------------------------------------------------------------------

const editingPhysical = ref(false);
const stoppingPhysical = ref(0);
function stopPhysical() {
	// Asks first only when stopping unreads pages that are on record.
	const lost = pagesUnread(currentReadings.value, book.value!.physicalPage, null);
	if (lost > 0) stoppingPhysical.value = lost;
	else edit((tx) => ops.setPhysicalProgress(tx, props.id, null, physicalPageCount(book.value!) ?? null));
}

// ---- Ownership ------------------------------------------------------------------------------

const editingPurchase = ref(false);
// Purchased keeps what is on record of the purchase; the other way clears it.
function setPurchased(purchased: boolean) {
	const purchase: ops.Purchase = purchased ? ops.purchaseOf(book.value!) : { acquisition: 'WISHLIST', ...ops.NO_PURCHASE };
	edit((tx) => ops.setPurchase(tx, props.id, purchase));
}

// ---- Notes, shelves, cover, removal ---------------------------------------------------------

const editingNote = ref<Note | 'new' | null>(null);
const checklist = ref<Map<string, number> | null>(null);
const openShelves = () => (checklist.value = library.shelfCounts([props.id]));
const saveShelves = (add: string[], remove: string[], create: string[]) =>
	edit((tx) => ops.setShelves(tx, [props.id], add, remove, create));

const coverInput = ref<HTMLInputElement>();
const coverFailed = ref(false);
async function pickCover(event: Event) {
	const input = event.target as HTMLInputElement;
	const file = input.files?.[0];
	input.value = '';
	if (!file) return;
	try {
		const name = await uploadCover(props.id, file);
		edit((tx) => ops.setCover(tx, props.id, name));
	} catch {
		coverFailed.value = true;
	}
}

const deleting = ref(false);
function remove() {
	edit((tx) => ops.deleteBook(tx, props.id));
	void router.replace({ name: 'library' });
}
</script>

<template>
	<template v-if="!book">
		<TopAppBar title="" :back="{ name: 'library' }" />
		<EmptyState face="downcast" icon="searchOff" :title="s.book.notFound" />
	</template>
	<template v-else>
		<TopAppBar :title="book.title" :back="{ name: 'library' }" :title-after="160">
			<button
				v-if="canFavorite(book)"
				class="icon-btn-tonal"
				:class="book.isFavorite ? 'bg-secondary text-on-secondary' : 'bg-surface-container-highest text-on-surface-variant'"
				:aria-label="book.isFavorite ? s.favorite.remove : s.favorite.add"
				:aria-pressed="book.isFavorite"
				:title="book.isFavorite ? s.favorite.remove : s.favorite.add"
				@click="edit((tx) => ops.setFavorite(tx, id, !book!.isFavorite))"
			>
				<AppIcon :name="book.isFavorite ? 'favoriteFill' : 'favorite'" />
			</button>
			<DropdownMenu>
				<template #trigger="{ toggle }">
					<button class="icon-btn" :aria-label="s.book.moreActions" :title="s.book.moreActions" @click="toggle">
						<AppIcon name="moreVert" />
					</button>
				</template>
				<template #default="{ close }">
					<a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener" class="menu-item" @click="close">
						<AppIcon name="openInNew" />{{ s.book.viewInBrowser }}
					</a>
					<button class="menu-item" @click="(coverInput?.click(), close())">
						<AppIcon name="addPhotoAlternateFill" />{{ s.book.coverChoose }}
					</button>
					<button v-if="book.customCover" class="menu-item" @click="(edit((tx) => ops.removeCover(tx, id)), close())">
						<AppIcon name="delete" />{{ s.book.coverRemove }}
					</button>
					<button class="menu-item" @click="((deleting = true), close())"><AppIcon name="deleteFill" />{{ s.action.remove }}</button>
				</template>
			</DropdownMenu>
			<input ref="coverInput" type="file" accept="image/*" class="hidden" @change="pickCover" />
		</TopAppBar>

		<article class="mx-auto flex max-w-[720px] flex-col gap-5 px-4 pt-2 pb-8">
			<BookHeader :book="book" rateable @rate="(rating) => edit((tx) => ops.setRating(tx, id, rating))" />

			<div class="flex flex-wrap items-center gap-2">
				<button
					v-if="book.state === 'COMPLETED' || book.state === 'DROPPED'"
					class="btn-tonal"
					@click="edit((tx) => ops.readAgain(tx, id))"
				>
					{{ s.book.readAgain }}
				</button>
				<RouterLink :to="{ name: 'bookEdit', params: { id } }" class="btn-tonal"
					><AppIcon name="editNoteFill" :size="18" />{{ s.form.editTitle }}</RouterLink
				>
				<button
					class="icon-btn-tonal w-12 bg-tertiary text-on-tertiary"
					:aria-label="s.shelves.addTo"
					:title="s.shelves.addTo"
					@click="openShelves"
				>
					<AppIcon name="shelvesFill" :size="20" />
				</button>
			</div>

			<StatusGroup v-if="canChangeState(book)" :state="stateOf(book)" @change="(state) => edit((tx) => ops.setState(tx, id, state))" />
			<ProgressCard v-if="ops.inLibrary(book)" :book="book" :reading-time-ms="readingTimeMs" :reading-number="readingNumber" />
			<PhysicalCard v-if="canTrackPhysical(book)" :book="book" @edit="editingPhysical = true" @stop="stopPhysical" />
			<SynopsisSection :book="book" />
			<OwnershipSection :book="book" @purchased="setPurchased" @edit="editingPurchase = true" />
			<NotesSection :notes="notes" @add="editingNote = 'new'" @open="editingNote = $event" />
			<HistorySection v-if="readThroughs.length" :read-throughs="readThroughs" />
			<ReviewsSection v-if="reviewable" :source-id="book.sourceId!" />
		</article>

		<PhysicalDialog
			v-if="editingPhysical"
			:book="book"
			:readings="currentReadings"
			@save="(page, count) => edit((tx) => ops.setPhysicalProgress(tx, id, page, count))"
			@close="editingPhysical = false"
		/>
		<ModalDialog v-if="stoppingPhysical" :title="s.physical.stopTitle" icon="history" icon-class="text-error" @close="stoppingPhysical = 0">
			<p>{{ s.physical.stopMessage(stoppingPhysical) }}</p>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
				<button
					class="btn-text text-error"
					@click="(edit((tx) => ops.setPhysicalProgress(tx, id, null, physicalPageCount(book!) ?? null)), close())"
				>
					{{ s.physical.stop }}
				</button>
			</template>
		</ModalDialog>
		<PurchaseDialog
			v-if="editingPurchase"
			:book="book"
			:last-currency="library.lastCurrency"
			:locations="library.purchaseLocations"
			@save="(purchase) => edit((tx) => ops.recordPurchase(tx, id, purchase))"
			@close="editingPurchase = false"
		/>
		<NoteDialog
			v-if="editingNote"
			:note="editingNote === 'new' ? undefined : editingNote"
			@save="(text) => edit((tx) => ops.saveNote(tx, id, editingNote === 'new' ? null : (editingNote as Note).id, text))"
			@remove="edit((tx) => ops.deleteNote(tx, (editingNote as Note).id))"
			@close="editingNote = null"
		/>
		<ShelfChecklistDialog
			v-if="checklist"
			:shelves="library.shelves"
			:on-shelf="checklist"
			:count="1"
			@save="saveShelves"
			@close="checklist = null"
		/>
		<DeleteBooksDialog v-if="deleting" :count="1" @confirm="remove" @close="deleting = false" />
		<ModalDialog v-if="coverFailed" :title="s.book.coverFailed" @close="coverFailed = false">
			<template #actions="{ close }"
				><button class="btn-text" @click="close">{{ s.action.ok }}</button></template
			>
		</ModalDialog>
	</template>
</template>
