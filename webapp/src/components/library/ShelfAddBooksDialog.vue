<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { searchBooks } from '@/lib/libraryView';
import { s } from '@/strings';
import type { Book, Shelf } from '@/types';

/** Picks books of the library that are not on [shelf] to put on it, as the phone's `ShelfAddBooksSheet`. */
const props = defineProps<{ shelf: Shelf; candidates: Book[] }>();
const emit = defineEmits<{ add: [ids: string[]]; close: [] }>();
const query = ref('');
const picked = ref(new Set<string>());
const shown = computed(() => {
	const groups = searchBooks(query.value, props.candidates);
	const found = new Set([...groups.library, ...groups.wishlist, ...groups.purchased].map((book) => book.id));
	return props.candidates.filter((book) => found.has(book.id));
});
function toggle(id: string) {
	if (picked.value.has(id)) picked.value.delete(id);
	else picked.value.add(id);
}
</script>

<template>
	<ModalDialog :title="shelf.name" wide @close="emit('close')">
		<p v-if="candidates.length === 0">{{ s.shelves.addBooksNone }}</p>
		<template v-else>
			<label class="mb-2 flex h-12 items-center gap-3 rounded-full bg-surface-container-highest px-4">
				<AppIcon name="search" />
				<input
					v-model="query"
					type="search"
					class="w-full bg-transparent text-on-surface outline-none"
					:placeholder="s.shelves.addBooksSearch"
					autofocus
				/>
			</label>
			<ul class="-mx-2 flex max-h-[50vh] flex-col overflow-y-auto text-on-surface">
				<li v-for="book in shown" :key="book.id">
					<button
						class="flex w-full cursor-pointer items-center gap-4 rounded-lg px-2 py-2 text-start hover:bg-on-surface/8"
						role="checkbox"
						:aria-checked="picked.has(book.id)"
						@click="toggle(book.id)"
					>
						<span
							class="flex size-5 shrink-0 items-center justify-center rounded-sm border-2"
							:class="picked.has(book.id) ? 'border-primary bg-primary text-on-primary' : 'border-on-surface-variant'"
						>
							<AppIcon v-if="picked.has(book.id)" name="check" :size="16" />
						</span>
						<span class="w-10 shrink-0"><BookCover :book="book" :show-title="false" rounded="rounded-sm" /></span>
						<span class="min-w-0">
							<span class="block truncate type-body-large">{{ book.title }}</span>
							<span class="block truncate type-body-medium text-on-surface-variant">{{ (book.authors ?? []).join(', ') }}</span>
						</span>
					</button>
				</li>
			</ul>
		</template>
		<template #actions="{ close }">
			<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
			<button v-if="candidates.length" class="btn-filled" :disabled="picked.size === 0" @click="(emit('add', [...picked]), close())">
				{{ s.shelves.addCount(picked.size) }}
			</button>
		</template>
	</ModalDialog>
</template>
