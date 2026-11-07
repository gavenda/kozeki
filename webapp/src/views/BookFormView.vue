<script setup lang="ts">
import { useEventListener } from '@vueuse/core';
import { computed, ref, useId } from 'vue';
import { onBeforeRouteLeave, useRouter, type RouteLocationRaw } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import Portrait from '@/components/Portrait.vue';
import SpeechBubble from '@/components/SpeechBubble.vue';
import { coverUrl, uploadCover } from '@/lib/covers';
import { toIsbn13 } from '@/lib/isbn';
import * as ops from '@/lib/library';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

/**
 * The details of a book typed in by hand, as the phone's `BookFormScreen`: a new one ([id] absent),
 * which is taken to be on the shelf already, or the one with [id] being edited. Leaving with changes
 * not saved asks first.
 */
const props = defineProps<{ id?: string }>();
const library = useLibrary();
const router = useRouter();
const book = computed(() => (props.id ? library.tables.books.get(props.id) : undefined));

const initial = {
	title: book.value?.title ?? '',
	authors: (book.value?.authors ?? []).join(', '),
	subtitle: book.value?.subtitle ?? '',
	publisher: book.value?.publisher ?? '',
	series: book.value?.series ?? '',
	seriesNumber: book.value?.seriesNumber?.toString() ?? '',
	publishedDate: book.value?.publishedDate ?? '',
	pageCount: book.value?.pageCount?.toString() ?? '',
	isbn: book.value?.isbn13 ?? book.value?.isbn10 ?? '',
	description: book.value?.description ?? '',
};
const form = ref({ ...initial });
const cover = ref<File | null>(null);
const coverPreview = ref<string | null>(null);
const removingCover = ref(false);
const saving = ref(false);
const saved = ref(false);

const authors = computed(() =>
	form.value.authors
		.split(/[,;]/)
		.map((name) => name.trim())
		.filter(Boolean),
);
const isbn = computed(() => (form.value.isbn.trim() ? toIsbn13(form.value.isbn) : ''));
const isbnInvalid = computed(() => isbn.value === null);
const canSave = computed(() => !!form.value.title.trim() && authors.value.length > 0 && !isbnInvalid.value && !saving.value);
const dirty = computed(() => JSON.stringify(form.value) !== JSON.stringify(initial) || cover.value != null || removingCover.value);

// The names already in the library, offered again while typing.
const ids = { authors: useId(), publishers: useId(), series: useId() };
const known = computed(() => {
	const all = library.books;
	const unique = (values: (string | undefined)[]) =>
		[...new Set(values.filter((value): value is string => !!value))].sort((a, b) => a.localeCompare(b));
	return {
		authors: unique(all.flatMap((row) => row.authors ?? [])),
		publishers: unique(all.map((row) => row.publisher)),
		series: unique(all.map((row) => row.series)),
	};
});

/** As the phone's `seriesNumberInput`: digits and one point, seven characters at most. */
function seriesNumberInput(typed: string): string {
	const kept = typed.replace(/,/g, '.').replace(/[^\d.]/g, '');
	const point = kept.indexOf('.');
	return (point < 0 ? kept : kept.slice(0, point + 1) + kept.slice(point + 1).replace(/\./g, '')).slice(0, 7);
}

const coverInput = ref<HTMLInputElement>();
function pickCover(event: Event) {
	const input = event.target as HTMLInputElement;
	const file = input.files?.[0];
	input.value = '';
	if (!file) return;
	cover.value = file;
	coverPreview.value = URL.createObjectURL(file);
	removingCover.value = false;
}
function dropCover() {
	if (cover.value) {
		cover.value = null;
		coverPreview.value = null;
	} else removingCover.value = true;
}
const shownCover = computed(() => ({
	title: form.value.title || s.form.title,
	coverFile: removingCover.value ? undefined : book.value?.customCover ? book.value.coverFile : undefined,
	coverUrl: coverPreview.value ?? (removingCover.value || book.value?.customCover ? undefined : book.value?.coverUrl),
}));
const hasOwnCover = computed(
	() => !!cover.value || (!!book.value?.customCover && !removingCover.value && coverUrl(book.value.coverFile) !== null),
);

async function save() {
	if (!canSave.value) return;
	saving.value = true;
	const details: ops.Details = {
		title: form.value.title,
		subtitle: form.value.subtitle,
		authors: authors.value,
		description: form.value.description,
		publisher: form.value.publisher,
		series: form.value.series,
		seriesNumber: form.value.series.trim() && form.value.seriesNumber ? Number(form.value.seriesNumber) : null,
		publishedDate: form.value.publishedDate,
		pageCount: form.value.pageCount ? Number(form.value.pageCount) : null,
		isbn13: isbn.value ?? '',
	};
	try {
		// One typed in by hand is taken to be on the shelf already.
		const id = props.id ?? library.edit((tx) => ops.addManually(tx, details, 'PURCHASED'));
		if (props.id) library.edit((tx) => ops.updateDetails(tx, id, details));
		if (cover.value) {
			const name = await uploadCover(id, cover.value);
			library.edit((tx) => ops.setCover(tx, id, name));
		} else if (removingCover.value) library.edit((tx) => ops.removeCover(tx, id));
		saved.value = true;
		if (props.id) router.back();
		else void router.replace({ name: 'book', params: { id } });
	} catch (e) {
		library.error = e instanceof Error ? e.message : String(e);
	} finally {
		saving.value = false;
	}
}

// ---- Leaving with changes --------------------------------------------------------------------

const leaving = ref<RouteLocationRaw | null>(null);
let discarded = false;
onBeforeRouteLeave((to) => {
	if (saved.value || discarded || !dirty.value) return true;
	leaving.value = to.fullPath;
	return false;
});
useEventListener(window, 'beforeunload', (event: BeforeUnloadEvent) => {
	if (dirty.value && !saved.value) event.preventDefault();
});
function discard() {
	discarded = true;
	void router.push(leaving.value!);
}
function close() {
	if (window.history.state?.back) router.back();
	else void router.push(props.id ? { name: 'book', params: { id: props.id } } : { name: 'library' });
}
</script>

<template>
	<header class="sticky top-0 z-30 flex h-16 items-center gap-1 bg-surface px-1">
		<button class="icon-btn size-12" :aria-label="s.action.close" @click="close"><AppIcon name="close" /></button>
		<h1 class="flex-1 truncate ps-1 type-title-large">{{ id ? s.form.editTitle : s.form.manualTitle }}</h1>
		<button class="btn-filled me-3" :disabled="!canSave" @click="save">{{ s.action.save }}</button>
	</header>

	<form class="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-2 pb-10" @submit.prevent="save">
		<div class="flex gap-4">
			<div class="flex w-28 shrink-0 flex-col items-center gap-1">
				<button
					type="button"
					class="relative w-full cursor-pointer"
					:aria-label="hasOwnCover ? s.book.coverChoose : s.form.coverAdd"
					@click="coverInput?.click()"
				>
					<BookCover :book="shownCover" />
					<span v-if="!hasOwnCover" class="absolute inset-0 flex items-center justify-center">
						<span class="rounded-full bg-surface-container-highest/80 p-2 text-on-surface"><AppIcon name="addPhotoAlternateFill" /></span>
					</span>
				</button>
				<button v-if="hasOwnCover" type="button" class="btn-text h-8" @click="dropCover">{{ s.action.remove }}</button>
				<input ref="coverInput" type="file" accept="image/*" class="hidden" @change="pickCover" />
			</div>
			<div class="flex min-w-0 flex-1 flex-col gap-3">
				<label>
					<span class="label">{{ s.form.title }}</span>
					<textarea v-model="form.title" rows="2" class="field resize-none" autocapitalize="words" :autofocus="!id" />
				</label>
				<label>
					<span class="label">{{ s.form.authors }}</span>
					<input v-model="form.authors" class="field" :list="ids.authors" autocapitalize="words" />
					<span class="supporting">{{ s.form.authorsHint }}</span>
				</label>
			</div>
		</div>
		<label>
			<span class="label">{{ s.form.subtitle }}</span>
			<input v-model="form.subtitle" class="field" />
		</label>
		<label>
			<span class="label">{{ s.book.detail.publisher }}</span>
			<input v-model="form.publisher" class="field" :list="ids.publishers" />
		</label>
		<div class="flex gap-3">
			<label class="flex-[2.4]">
				<span class="label">{{ s.book.detail.series }}</span>
				<input v-model="form.series" class="field" :list="ids.series" />
			</label>
			<label class="flex-1">
				<span class="label">{{ s.book.detail.seriesNumber }}</span>
				<input
					:value="form.seriesNumber"
					class="field"
					inputmode="decimal"
					:disabled="!form.series.trim()"
					@input="form.seriesNumber = seriesNumberInput(($event.target as HTMLInputElement).value)"
				/>
			</label>
		</div>
		<div class="flex gap-3">
			<label class="flex-[1.4]">
				<span class="label">{{ s.book.detail.published }}</span>
				<input v-model="form.publishedDate" class="field" placeholder="2026-11-08" />
			</label>
			<label class="flex-1">
				<span class="label">{{ s.book.detail.pages }}</span>
				<input
					:value="form.pageCount"
					class="field"
					inputmode="numeric"
					@input="form.pageCount = ($event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 5)"
				/>
			</label>
		</div>
		<label>
			<span class="label">{{ s.book.detail.isbn }}</span>
			<input v-model="form.isbn" class="field" :class="{ 'field-error': isbnInvalid }" maxlength="20" inputmode="numeric" />
			<span v-if="isbnInvalid" class="supporting text-error">{{ s.form.isbnInvalid }}</span>
		</label>
		<label>
			<span class="label">{{ s.form.description }}</span>
			<textarea v-model="form.description" rows="5" class="field" />
		</label>
		<div v-if="!id" class="flex items-center justify-center gap-2 pt-4">
			<Portrait face="studious" :width="96" />
			<SpeechBubble class="max-w-[360px]">{{ s.form.endBubble }}</SpeechBubble>
		</div>
		<datalist :id="ids.authors"><option v-for="name in known.authors" :key="name" :value="name" /></datalist>
		<datalist :id="ids.publishers"><option v-for="name in known.publishers" :key="name" :value="name" /></datalist>
		<datalist :id="ids.series"><option v-for="name in known.series" :key="name" :value="name" /></datalist>
	</form>

	<ModalDialog v-if="leaving" :title="s.form.discardTitle" icon="editOffFill" @close="leaving = null">
		<p>{{ id ? s.form.discardMessage : s.form.discardBookMessage }}</p>
		<template #actions="{ close: dismiss }">
			<button class="btn-text" @click="dismiss">{{ s.form.keepEditing }}</button>
			<button class="btn-text" @click="discard">{{ s.form.discard }}</button>
		</template>
	</ModalDialog>
</template>
