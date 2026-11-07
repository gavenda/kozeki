<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { search } from '@/lib/catalogue';
import { hardcoverSignedIn } from '@/lib/hardcover';
import * as ops from '@/lib/library';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';
import type { Acquisition } from '@/types';

const library = useLibrary();
const router = useRouter();

const query = ref('');
const results = ref<ops.Found[] | null>(null);
const searching = ref(false);
const failed = ref<string | null>(null);
const sourceName = hardcoverSignedIn() ? 'Hardcover' : 'Google Books';

async function run() {
	if (!query.value.trim()) return;
	searching.value = true;
	failed.value = null;
	try {
		results.value = await search(query.value);
	} catch (e) {
		failed.value = e instanceof Error ? e.message : String(e);
	} finally {
		searching.value = false;
	}
}

const added = computed(() => new Set(library.books.map((book) => `${book.source}|${book.sourceId}`)));

function add(found: ops.Found, acquisition: Acquisition) {
	const id = library.edit((tx) => ops.addFound(tx, found, acquisition));
	void router.push({ name: 'book', params: { id } });
}

const manual = ref<ops.Details | null>(null);
const authorsText = ref('');
function openManual() {
	manual.value = {
		title: query.value,
		subtitle: '',
		authors: [],
		description: '',
		publisher: '',
		series: '',
		seriesNumber: null,
		publishedDate: '',
		pageCount: null,
		isbn13: '',
	};
	authorsText.value = '';
}
function saveManual(acquisition: Acquisition) {
	const details = { ...manual.value!, authors: authorsText.value.split('\n') };
	const id = library.edit((tx) => ops.addManually(tx, details, acquisition));
	void router.push({ name: 'book', params: { id } });
}
</script>

<template>
	<div class="flex flex-col gap-4">
		<div class="flex items-center gap-2">
			<button class="icon-btn" :aria-label="s.nav.library" @click="router.back()"><AppIcon name="arrowBack" /></button>
			<form
				class="flex h-12 flex-1 items-center gap-3 rounded-full bg-surface-container-high px-4 text-on-surface-variant"
				@submit.prevent="run"
			>
				<AppIcon name="search" />
				<input
					v-model="query"
					type="search"
					:placeholder="s.add.search"
					class="w-full bg-transparent text-on-surface outline-none"
					autofocus
				/>
			</form>
		</div>
		<div class="flex items-center justify-between px-1 text-xs text-on-surface-variant">
			<span>{{ s.add.source(sourceName) }}</span>
			<button class="btn-text h-8" @click="openManual">{{ s.add.manual }}</button>
		</div>

		<EmptyState v-if="searching" face="studious" :title="s.add.promptTitle" />
		<EmptyState v-else-if="failed" face="dismayed" :title="s.add.errorTitle" :message="failed" />
		<EmptyState v-else-if="results === null" face="studious" :title="s.add.promptTitle" :message="s.add.promptMessage" />
		<EmptyState v-else-if="results.length === 0" face="worried" :title="s.add.noResultsTitle" :message="s.add.noResultsMessage" />

		<ul v-else class="flex flex-col gap-3">
			<li v-for="found in results" :key="found.sourceId" class="card flex gap-4 p-4">
				<img
					v-if="found.coverUrl"
					:src="found.coverUrl"
					alt=""
					class="aspect-[2/3] w-20 shrink-0 rounded-lg object-cover"
					referrerpolicy="no-referrer"
				/>
				<div v-else class="aspect-[2/3] w-20 shrink-0 rounded-lg bg-surface-container-highest" />
				<div class="flex min-w-0 flex-1 flex-col gap-1">
					<p class="font-medium">{{ found.title }}</p>
					<p class="text-sm text-on-surface-variant">{{ found.authors.join(', ') }}</p>
					<p class="text-xs text-on-surface-variant">
						{{ [found.series, found.publishedDate, found.pageCount ? `${found.pageCount} pages` : null].filter(Boolean).join(' · ') }}
					</p>
					<div class="mt-auto flex flex-wrap gap-2 pt-2">
						<span v-if="added.has(`${found.source}|${found.sourceId}`)" class="text-sm text-primary">{{ s.add.added }}</span>
						<template v-else>
							<button class="btn-tonal h-9" @click="add(found, 'WISHLIST')">
								<AppIcon name="bookmarkAdd" :size="18" />{{ s.add.wishlist }}
							</button>
							<button class="btn-text h-9" @click="add(found, 'PURCHASED')">
								<AppIcon name="shoppingBag" :size="18" />{{ s.add.owned }}
							</button>
						</template>
					</div>
				</div>
			</li>
		</ul>

		<ModalDialog v-if="manual" :title="s.add.manualTitle" @close="manual = null">
			<div class="grid grid-cols-2 gap-3">
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.title }}</span
					><input v-model="manual.title" class="field"
				/></label>
				<label class="col-span-2"
					><span class="label">{{ s.book.fields.authors }}</span
					><textarea v-model="authorsText" rows="2" class="field" />
				</label>
				<label
					><span class="label">{{ s.book.fields.pageCount }}</span
					><input v-model.number="manual.pageCount" type="number" class="field"
				/></label>
				<label
					><span class="label">{{ s.book.fields.isbn }}</span
					><input v-model="manual.isbn13" class="field"
				/></label>
			</div>
			<template #actions>
				<button class="btn-text" :disabled="!manual.title.trim()" @click="saveManual('PURCHASED')">{{ s.add.owned }}</button>
				<button class="btn-filled" :disabled="!manual.title.trim()" @click="saveManual('WISHLIST')">{{ s.add.wishlist }}</button>
			</template>
		</ModalDialog>
	</div>
</template>
