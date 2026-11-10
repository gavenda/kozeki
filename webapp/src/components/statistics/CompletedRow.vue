<script setup lang="ts">
import { Card } from 'tailwind-material3';
import { RouterLink } from 'vue-router';
import BookCover from '@/components/BookCover.vue';
import RatingBar from '@/components/RatingBar.vue';
import { formatDay } from '@/lib/format';
import type { CompletedBook } from '@/lib/stats';

/** A book finished, with the day and its rating, as the phone's `CompletedRow`. */
defineProps<{ entry: CompletedBook }>();
</script>

<template>
	<Card
		:as="RouterLink"
		:to="{ name: 'book', params: { id: entry.book.id } }"
		interactive
		:padded="false"
		class="flex! items-center gap-3 bg-surface-container! p-3"
	>
		<span class="w-10 shrink-0"><BookCover :book="entry.book" :show-title="false" rounded="rounded-md" /></span>
		<span class="min-w-0 flex-1">
			<span class="block truncate type-title-small">{{ entry.book.title }}</span>
			<span class="block type-body-small text-on-surface-variant">{{ formatDay(entry.finishedOn) }}</span>
		</span>
		<RatingBar v-if="entry.book.rating != null" :rating="entry.book.rating" :size="14" />
	</Card>
</template>
