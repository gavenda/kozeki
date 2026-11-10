<script setup lang="ts">
import { useEventListener, useWindowScroll } from '@vueuse/core';
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
import { AppBar, Button, IconButton, TextField } from 'tailwind-material3';

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
const { y } = useWindowScroll();
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
	<AppBar class="sticky top-0 z-30" :scrolled="y > 0">
		<template #leading>
			<IconButton variant="standard" :label="s.action.close" @click="close"><AppIcon name="close" /></IconButton>
		</template>
		{{ id ? s.form.editTitle : s.form.manualTitle }}
		<template #trailing>
			<Button class="me-3" :disabled="!canSave" @click="save">{{ s.action.save }}</Button>
		</template>
	</AppBar>

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
				<Button variant="text" size="xs" v-if="hasOwnCover" type="button" @click="dropCover">{{ s.action.remove }}</Button>
				<input ref="coverInput" type="file" accept="image/*" class="hidden" @change="pickCover" />
			</div>
			<div class="flex min-w-0 flex-1 flex-col gap-3">
				<TextField
					v-model="form.title"
					variant="outlined"
					:label="s.form.title"
					multiline
					:rows="2"
					autocapitalize="words"
					:autofocus="!id"
				/>
				<TextField
					v-model="form.authors"
					variant="outlined"
					:label="s.form.authors"
					:supporting-text="s.form.authorsHint"
					:list="ids.authors"
					autocapitalize="words"
				/>
			</div>
		</div>
		<TextField v-model="form.subtitle" variant="outlined" :label="s.form.subtitle" />
		<TextField v-model="form.publisher" variant="outlined" :label="s.book.detail.publisher" :list="ids.publishers" />
		<div class="flex gap-3">
			<TextField v-model="form.series" variant="outlined" :label="s.book.detail.series" class="min-w-0 flex-[2.4]" :list="ids.series" />
			<TextField
				:model-value="form.seriesNumber"
				variant="outlined"
				:label="s.book.detail.seriesNumber"
				class="min-w-0 flex-1"
				inputmode="decimal"
				:disabled="!form.series.trim()"
				@update:model-value="form.seriesNumber = seriesNumberInput(String($event ?? ''))"
			/>
		</div>
		<div class="flex gap-3">
			<TextField
				v-model="form.publishedDate"
				variant="outlined"
				:label="s.book.detail.published"
				class="min-w-0 flex-[1.4]"
				placeholder="2026-11-08"
			/>
			<TextField
				:model-value="form.pageCount"
				variant="outlined"
				:label="s.book.detail.pages"
				class="min-w-0 flex-1"
				inputmode="numeric"
				@update:model-value="
					form.pageCount = String($event ?? '')
						.replace(/\D/g, '')
						.slice(0, 5)
				"
			/>
		</div>
		<TextField
			v-model="form.isbn"
			variant="outlined"
			:label="s.book.detail.isbn"
			:error="isbnInvalid"
			:error-text="s.form.isbnInvalid"
			maxlength="20"
			inputmode="numeric"
		/>
		<TextField v-model="form.description" variant="outlined" :label="s.form.description" multiline :rows="5" />
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
			<Button variant="text" @click="dismiss">{{ s.form.keepEditing }}</Button>
			<Button variant="text" @click="discard">{{ s.form.discard }}</Button>
		</template>
	</ModalDialog>
</template>
