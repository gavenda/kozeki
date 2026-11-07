<script setup lang="ts">
import { computed } from 'vue';
import { coverUrl } from '@/lib/covers';
import type { Book } from '@/types';

const props = defineProps<{ book: Book }>();

// The account's own copy of the cover first, then the catalogue's, as the phone does.
const src = computed(() => {
	const own = coverUrl(props.book.coverFile);
	if (own) return own;
	if (own === undefined && props.book.coverFile) return null;
	return props.book.coverUrl ?? null;
});
</script>

<template>
	<div class="relative aspect-[2/3] w-full overflow-hidden rounded-xl bg-surface-container-highest">
		<img v-if="src" :src="src" :alt="book.title" class="size-full object-cover" loading="lazy" referrerpolicy="no-referrer" />
		<div v-else class="flex size-full items-end p-3">
			<span class="line-clamp-4 text-sm font-medium text-on-surface-variant">{{ book.title }}</span>
		</div>
	</div>
</template>
