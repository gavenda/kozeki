<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import StarRating from '@/components/StarRating.vue';
import { uploadCover } from '@/lib/covers';
import { epochDayOf } from '@/lib/dates';
import { formatDay, formatPercent, formatPrice, parsePrice, priceToInput } from '@/lib/format';
import * as ops from '@/lib/library';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { ReadingState } from '@/types';

const props = defineProps<{ id: string }>();
const library = useLibrary();
const router = useRouter();

const book = computed(() => {
	const row = library.tables.books.get(props.id);
	return row && ops.alive(row) ? row : undefined;
});
const wished = computed(() => book.value?.acquisition === 'WISHLIST');
// Only a web address is a link: a synced row could hold anything, `javascript:` included.
const sourceLink = computed(() => (/^https?:\/\//i.test(book.value?.sourceUrl ?? '') ? book.value!.sourceUrl : undefined));
const STATES: ReadingState[] = ['PLANNED', 'READING', 'PAUSED', 'COMPLETED', 'DROPPED'];

const notes = computed(() =>
	[...library.tables.notes.values()]
		.filter((note) => ops.alive(note) && note.bookId === props.id)
		.sort((a, b) => b.sync.createdAt - a.sync.createdAt),
);
const readThroughs = computed(() =>
	[...library.tables.readThroughs.values()].filter((row) => ops.alive(row) && row.bookId === props.id).sort((a, b) => b.number - a.number),
);
const onShelf = computed(
	() =>
		new Set([...library.tables.shelfBooks.values()].filter((row) => ops.alive(row) && row.bookId === props.id).map((row) => row.shelfId)),
);

// ---- Progress -----------------------------------------------------------------------------

const page = ref<string>('');
const pageCount = ref<string>('');
function resetPage() {
	page.value = book.value?.physicalPage?.toString() ?? '';
	pageCount.value = (book.value?.physicalPageCount ?? book.value?.pageCount)?.toString() ?? '';
}
watch(() => [book.value?.physicalPage, book.value?.physicalPageCount, book.value?.pageCount], resetPage, { immediate: true });
function savePage() {
	const reached = page.value.trim() === '' ? null : Number(page.value);
	const total = pageCount.value.trim() === '' ? null : Number(pageCount.value);
	library.edit((tx) => ops.setPhysicalProgress(tx, props.id, reached, total));
}

// ---- Purchase -----------------------------------------------------------------------------

const purchasing = ref(false);
const purchase = ref({ price: '', currency: '', date: '', location: '', memo: '' });
function openPurchase() {
	const current = book.value!;
	const currency = current.purchaseCurrency ?? library.setting<string>('lastCurrency', 'USD');
	const day = current.purchasedOn ?? epochDayOf(Date.now());
	purchase.value = {
		price: current.purchasePriceMinor != null ? priceToInput(current.purchasePriceMinor, currency) : '',
		currency,
		date: new Date(day * 86_400_000).toISOString().slice(0, 10),
		location: current.purchaseLocation ?? '',
		memo: current.purchaseMemo ?? '',
	};
	purchasing.value = true;
}
function savePurchase(close: () => void) {
	const { price, currency, date, location, memo } = purchase.value;
	const code = currency.trim().toUpperCase();
	const priceMinor = price.trim() ? parsePrice(price, code) : null;
	const [y, m, d] = date.split('-').map(Number);
	library.edit((tx) => {
		ops.setPurchase(tx, props.id, {
			acquisition: 'PURCHASED',
			priceMinor,
			currency: code || null,
			purchasedOn: y ? Math.floor(Date.UTC(y, m - 1, d) / 86_400_000) : null,
			location,
			memo,
		});
		if (priceMinor != null && code) ops.setSetting(tx, 'lastCurrency', code);
	});
	close();
}
function notPurchased() {
	library.edit((tx) =>
		ops.setPurchase(tx, props.id, {
			acquisition: 'WISHLIST',
			priceMinor: null,
			currency: null,
			purchasedOn: null,
			location: null,
			memo: null,
		}),
	);
}

// ---- Notes --------------------------------------------------------------------------------

const noteDraft = ref<{ id: string | null; text: string } | null>(null);
function saveNote(close: () => void) {
	const draft = noteDraft.value!;
	if (draft.text.trim()) library.edit((tx) => ops.saveNote(tx, props.id, draft.id, draft.text));
	close();
}

// ---- Details ------------------------------------------------------------------------------

const editing = ref(false);
const details = ref<ops.Details>({
	title: '',
	subtitle: '',
	authors: [],
	description: '',
	publisher: '',
	series: '',
	seriesNumber: null,
	publishedDate: '',
	pageCount: null,
	isbn13: '',
});
const authorsText = ref('');
function openDetails() {
	const b = book.value!;
	details.value = {
		title: b.title,
		subtitle: b.subtitle ?? '',
		authors: b.authors ?? [],
		description: b.description ?? '',
		publisher: b.publisher ?? '',
		series: b.series ?? '',
		seriesNumber: b.seriesNumber ?? null,
		publishedDate: b.publishedDate ?? '',
		pageCount: b.pageCount ?? null,
		isbn13: b.isbn13 ?? '',
	};
	authorsText.value = (b.authors ?? []).join('\n');
	editing.value = true;
}
function saveDetails(close: () => void) {
	library.edit((tx) => ops.updateDetails(tx, props.id, { ...details.value, authors: authorsText.value.split('\n') }));
	close();
}

const coverInput = ref<HTMLInputElement>();
const uploading = ref(false);
async function pickCover(event: Event) {
	const file = (event.target as HTMLInputElement).files?.[0];
	if (!file) return;
	uploading.value = true;
	try {
		const name = await uploadCover(props.id, file);
		library.edit((tx) => ops.setCover(tx, props.id, name));
	} catch (e) {
		library.error = e instanceof Error ? e.message : String(e);
	} finally {
		uploading.value = false;
	}
}

// ---- Shelves and removal ------------------------------------------------------------------

const newShelf = ref<string | null>(null);
function createShelf(close: () => void) {
	const name = newShelf.value?.trim();
	if (name) library.edit((tx) => ops.setOnShelf(tx, ops.createShelf(tx, name), props.id, true));
	close();
}

const deleting = ref(false);
function remove() {
	library.edit((tx) => ops.deleteBook(tx, props.id));
	void router.replace({ name: 'library' });
}
</script>

<template>
	<EmptyState v-if="!book" face="downcast" :title="s.book.notFound">
		<RouterLink :to="{ name: 'library' }" class="btn-tonal">{{ s.nav.library }}</RouterLink>
	</EmptyState>

	<article v-else class="flex flex-col gap-8">
		<button class="icon-btn -ml-2" :aria-label="s.nav.library" @click="router.back()"><AppIcon name="arrowBack" /></button>

		<header class="flex flex-col gap-6 sm:flex-row">
			<div class="w-40 shrink-0 self-center sm:self-start">
				<BookCover :book="book" />
				<button class="btn-text mt-2 w-full" :disabled="uploading" @click="coverInput?.click()">
					<AppIcon name="photoCamera" :size="18" />{{ s.book.changeCover }}
				</button>
				<input ref="coverInput" type="file" accept="image/*" class="hidden" @change="pickCover" />
			</div>
			<div class="flex min-w-0 flex-1 flex-col gap-2">
				<h1 class="text-3xl font-medium">{{ book.title }}</h1>
				<p v-if="book.subtitle" class="text-lg text-on-surface-variant">{{ book.subtitle }}</p>
				<p class="text-on-surface-variant">{{ s.book.by(book.authors ?? []) }}</p>
				<p v-if="book.series" class="text-sm text-on-surface-variant">
					{{ book.series }}<template v-if="book.seriesNumber != null"> · #{{ book.seriesNumber }}</template>
				</p>
				<div class="mt-2 flex flex-wrap items-center gap-3">
					<StarRating :model-value="book.rating" @update:model-value="(value) => library.edit((tx) => ops.setRating(tx, id, value))" />
					<button
						v-if="!wished"
						class="icon-btn"
						:aria-label="book.isFavorite ? s.book.unfavorite : s.book.favorite"
						@click="library.edit((tx) => ops.setFavorite(tx, id, !book!.isFavorite))"
					>
						<AppIcon :name="book.isFavorite ? 'favoriteFill' : 'favorite'" :class="book.isFavorite ? 'text-error' : ''" />
					</button>
				</div>

				<div class="mt-3 flex flex-wrap gap-2">
					<button
						v-for="state in STATES"
						:key="state"
						class="chip"
						:class="{ 'chip-on': (book.state ?? 'PLANNED') === state }"
						:disabled="wished && state !== 'PLANNED'"
						@click="library.edit((tx) => ops.setState(tx, id, state))"
					>
						{{ s.state[state] }}
					</button>
					<button
						v-if="book.state === 'COMPLETED' || book.state === 'DROPPED'"
						class="btn-text h-8"
						@click="library.edit((tx) => ops.readAgain(tx, id))"
					>
						{{ s.book.readAgain }}
					</button>
				</div>
			</div>
		</header>

		<section class="grid gap-4 md:grid-cols-2">
			<div class="card flex flex-col gap-3 p-5">
				<h2 class="section-title">{{ s.book.progress }}</h2>
				<div v-if="(book.progression ?? 0) > 0" class="flex items-center gap-3">
					<div class="h-2 flex-1 overflow-hidden rounded-full bg-surface-container-highest">
						<div class="h-full rounded-full bg-primary" :style="{ width: formatPercent(book.progression ?? 0) }" />
					</div>
					<span class="text-sm text-on-surface-variant">{{ formatPercent(book.progression ?? 0) }}</span>
				</div>
				<p v-if="book.chapterTitle" class="text-sm text-on-surface-variant">{{ book.chapterTitle }}</p>
				<form v-if="!wished" class="flex flex-wrap items-end gap-2" @submit.prevent="savePage">
					<label class="flex-1">
						<span class="label">{{ s.book.physicalPage }}</span>
						<input v-model="page" class="field" inputmode="numeric" pattern="\d*" />
					</label>
					<label class="w-24">
						<span class="label">{{ s.book.physicalPageOf }}</span>
						<input v-model="pageCount" class="field" inputmode="numeric" pattern="\d*" />
					</label>
					<button class="btn-tonal">{{ s.book.physicalSave }}</button>
				</form>
			</div>

			<div class="card flex flex-col gap-3 p-5">
				<h2 class="section-title">{{ s.purchase.title }}</h2>
				<p class="text-sm">{{ s.acquisition[book.acquisition ?? 'WISHLIST'] }}</p>
				<template v-if="book.acquisition === 'PURCHASED'">
					<p class="text-sm text-on-surface-variant">
						{{ book.purchasePriceMinor != null ? formatPrice(book.purchasePriceMinor, book.purchaseCurrency) : s.purchase.noPrice }}
						<template v-if="book.purchasedOn != null"> · {{ s.purchase.boughtOn(formatDay(book.purchasedOn)) }}</template>
					</p>
					<p v-if="book.purchaseLocation" class="text-sm text-on-surface-variant">{{ book.purchaseLocation }}</p>
					<p v-if="book.purchaseMemo" class="text-sm text-on-surface-variant">{{ book.purchaseMemo }}</p>
				</template>
				<div class="flex flex-wrap gap-2">
					<button class="btn-tonal" @click="openPurchase">
						{{ book.acquisition === 'PURCHASED' ? s.book.edit : s.purchase.markPurchased }}
					</button>
					<button v-if="book.acquisition === 'PURCHASED'" class="btn-text" @click="notPurchased">{{ s.purchase.markNotPurchased }}</button>
				</div>
			</div>
		</section>

		<section class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.shelves.title }}</h2>
			<div class="flex flex-wrap gap-2">
				<button
					v-for="shelf in library.shelves"
					:key="shelf.id"
					class="chip"
					:class="{ 'chip-on': onShelf.has(shelf.id) }"
					@click="library.edit((tx) => ops.setOnShelf(tx, shelf.id, id, !onShelf.has(shelf.id)))"
				>
					<AppIcon v-if="onShelf.has(shelf.id)" name="check" :size="18" />{{ shelf.name }}
				</button>
				<button class="chip" @click="newShelf = ''"><AppIcon name="add" :size="18" />{{ s.shelves.new }}</button>
			</div>
		</section>

		<section class="flex flex-col gap-3">
			<div class="flex items-center justify-between">
				<h2 class="section-title">{{ s.notes.title }}</h2>
				<button class="btn-text" @click="noteDraft = { id: null, text: '' }"><AppIcon name="add" :size="18" />{{ s.notes.add }}</button>
			</div>
			<p v-if="notes.length === 0" class="px-1 text-sm text-on-surface-variant">{{ s.notes.empty }}</p>
			<ul class="flex flex-col gap-2">
				<li v-for="note in notes" :key="note.id" class="card flex gap-3 p-4">
					<div class="min-w-0 flex-1">
						<p v-if="note.chapterTitle" class="mb-1 text-xs text-on-surface-variant">{{ note.chapterTitle }}</p>
						<p class="text-sm whitespace-pre-wrap">{{ note.text }}</p>
					</div>
					<button class="icon-btn" :aria-label="s.book.edit" @click="noteDraft = { id: note.id, text: note.text }">
						<AppIcon name="edit" :size="20" />
					</button>
					<button class="icon-btn" :aria-label="s.notes.remove" @click="library.edit((tx) => ops.deleteNote(tx, note.id))">
						<AppIcon name="delete" :size="20" />
					</button>
				</li>
			</ul>
		</section>

		<section v-if="readThroughs.length" class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.book.readThroughs }}</h2>
			<ul class="card divide-y divide-outline-variant/40">
				<li v-for="row in readThroughs" :key="row.id" class="flex flex-wrap justify-between gap-2 px-5 py-3 text-sm">
					<span class="font-medium">{{ s.book.readThrough(row.number) }}</span>
					<span class="text-on-surface-variant">
						{{ s.book.started(formatDay(epochDayOf(row.startedAt))) }}
						<template v-if="row.finishedOn != null">
							· {{ row.outcome === 'DROPPED' ? s.book.dropped(formatDay(row.finishedOn)) : s.book.finished(formatDay(row.finishedOn)) }}
						</template>
					</span>
				</li>
			</ul>
		</section>

		<section class="flex flex-col gap-3">
			<div class="flex items-center justify-between">
				<h2 class="section-title">{{ s.book.details }}</h2>
				<button class="btn-text" @click="openDetails"><AppIcon name="edit" :size="18" />{{ s.book.edit }}</button>
			</div>
			<div class="card flex flex-col gap-3 p-5 text-sm">
				<p v-if="book.description" class="whitespace-pre-line">{{ book.description }}</p>
				<dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-on-surface-variant">
					<template v-if="book.publisher"
						><dt>{{ s.book.fields.publisher }}</dt>
						<dd class="text-on-surface">{{ book.publisher }}</dd></template
					>
					<template v-if="book.publishedDate"
						><dt>{{ s.book.fields.publishedDate }}</dt>
						<dd class="text-on-surface">{{ book.publishedDate }}</dd></template
					>
					<template v-if="book.pageCount"
						><dt>{{ s.book.fields.pageCount }}</dt>
						<dd class="text-on-surface">{{ book.pageCount }}</dd></template
					>
					<template v-if="book.isbn13"
						><dt>{{ s.book.fields.isbn }}</dt>
						<dd class="text-on-surface">{{ book.isbn13 }}</dd></template
					>
				</dl>
				<a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener" class="text-primary hover:underline">{{
					s.book.onSource
				}}</a>
			</div>
		</section>

		<div>
			<button class="btn-text text-error hover:bg-error/10" @click="deleting = true">
				<AppIcon name="delete" :size="18" />{{ s.book.delete }}
			</button>
		</div>

		<ModalDialog v-if="purchasing" :title="s.purchase.title" @close="purchasing = false">
			<div class="grid grid-cols-[1fr_6rem] gap-3">
				<label
					><span class="label">{{ s.purchase.price }}</span
					><input v-model="purchase.price" class="field" inputmode="decimal"
				/></label>
				<label
					><span class="label">{{ s.purchase.currency }}</span
					><input v-model="purchase.currency" class="field uppercase" maxlength="3"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.purchase.date }}</span
					><input v-model="purchase.date" type="date" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.purchase.location }}</span
					><input v-model="purchase.location" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.purchase.memo }}</span
					><textarea v-model="purchase.memo" rows="2" class="field" />
				</label>
			</div>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.book.cancel }}</button>
				<button class="btn-filled" @click="savePurchase(close)">{{ s.purchase.save }}</button>
			</template>
		</ModalDialog>

		<ModalDialog v-if="noteDraft" :title="noteDraft.id ? s.book.edit : s.notes.add" @close="noteDraft = null">
			<textarea v-model="noteDraft.text" rows="6" class="field" :placeholder="s.notes.hint" autofocus />
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.book.cancel }}</button>
				<button class="btn-filled" :disabled="!noteDraft.text.trim()" @click="saveNote(close)">{{ s.notes.save }}</button>
			</template>
		</ModalDialog>

		<ModalDialog v-if="newShelf !== null" :title="s.shelves.new" @close="newShelf = null">
			<label
				><span class="label">{{ s.shelves.name }}</span
				><input v-model="newShelf" class="field" autofocus
			/></label>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.book.cancel }}</button>
				<button class="btn-filled" :disabled="!newShelf?.trim()" @click="createShelf(close)">{{ s.shelves.create }}</button>
			</template>
		</ModalDialog>

		<ModalDialog v-if="editing" :title="s.book.edit" @close="editing = false">
			<div class="grid max-h-[60vh] grid-cols-2 gap-3 overflow-y-auto p-1">
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.title }}</span
					><input v-model="details.title" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.subtitle }}</span
					><input v-model="details.subtitle" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.authors }}</span
					><textarea v-model="authorsText" rows="2" class="field" />
				</label>
				<label
					><span class="label">{{ s.book.fields.series }}</span
					><input v-model="details.series" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.seriesNumber }}</span
					><input v-model.number="details.seriesNumber" type="number" step="0.5" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.publisher }}</span
					><input v-model="details.publisher" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.publishedDate }}</span
					><input v-model="details.publishedDate" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.pageCount }}</span
					><input v-model.number="details.pageCount" type="number" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.isbn }}</span
					><input v-model="details.isbn13" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.description }}</span
					><textarea v-model="details.description" rows="5" class="field" />
				</label>
			</div>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.book.cancel }}</button>
				<button class="btn-filled" :disabled="!details.title.trim()" @click="saveDetails(close)">{{ s.book.save }}</button>
			</template>
		</ModalDialog>

		<ModalDialog v-if="deleting" :title="s.book.deleteTitle" @close="deleting = false">
			<p class="text-sm text-on-surface-variant">{{ s.book.deleteMessage }}</p>
			<template #actions="{ close }">
				<button class="btn-text" @click="close">{{ s.book.deleteKeep }}</button>
				<button class="btn-danger" @click="remove">{{ s.book.deleteConfirm }}</button>
			</template>
		</ModalDialog>
	</article>
</template>
