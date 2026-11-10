<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { searchBooks } from '@/lib/libraryView';
import { s } from '@/strings';
import type { Book, Shelf } from '@/types';
import { Button, Checkbox, TextField } from 'tailwind-material3';

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
	<ModalDialog :title="shelf.name" @close="emit('close')">
		<p v-if="candidates.length === 0">{{ s.shelves.addBooksNone }}</p>
		<template v-else>
			<TextField v-model="query" type="search" :placeholder="s.shelves.addBooksSearch" class="mb-2 w-full" autofocus>
				<template #icon><AppIcon name="search" /></template>
			</TextField>
			<ul class="flex max-h-[50vh] flex-col overflow-y-auto text-on-surface">
				<li v-for="book in shown" :key="book.id">
					<Checkbox :model-value="picked.has(book.id)" class="w-full gap-2 py-1" @update:model-value="toggle(book.id)">
						<span class="flex min-w-0 items-center gap-4">
							<span class="w-10 shrink-0"><BookCover :book="book" :show-title="false" rounded="rounded-sm" /></span>
							<span class="min-w-0">
								<span class="block truncate type-body-large">{{ book.title }}</span>
								<span class="block truncate type-body-medium text-on-surface-variant">{{ (book.authors ?? []).join(', ') }}</span>
							</span>
						</span>
					</Checkbox>
				</li>
			</ul>
		</template>
		<template #actions="{ close }">
			<Button variant="text" @click="close">{{ s.action.cancel }}</Button>
			<Button v-if="candidates.length" :disabled="picked.size === 0" @click="(emit('add', [...picked]), close())">
				{{ s.shelves.addCount(picked.size) }}
			</Button>
		</template>
	</ModalDialog>
</template>
