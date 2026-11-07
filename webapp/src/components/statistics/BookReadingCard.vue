<script setup lang="ts">
import { computed } from 'vue';
import BookCover from '@/components/BookCover.vue';
import { formatDuration } from '@/lib/format';
import { pagesPerHour, type BookReading } from '@/lib/stats';
import { s } from '@/strings';

/** What was read of one book over the day or the period, as the phone's `BookReadingCard`. */
const props = defineProps<{ reading: BookReading }>();
const lines = computed(() => {
	const r = props.reading;
	const span = r.startPosition != null && r.endPosition != null ? s.stats.pageSpan(r.startPosition, r.endPosition) : null;
	if (r.physical) return [[s.book.progressPhysical, span].filter(Boolean).join(' · ')];
	const rate = pagesPerHour(r);
	return [
		span,
		r.pages > 0 ? s.stats.pagesGained(r.pages, Math.round(r.progressGained * 100)) : null,
		rate != null ? s.stats.pagesPerHour(rate) : null,
	].filter((line): line is string => !!line);
});
</script>

<template>
	<RouterLink
		:to="{ name: 'book', params: { id: reading.book.id } }"
		class="card flex gap-3 p-3 transition-colors hover:bg-surface-container-high"
	>
		<span class="w-16 shrink-0"><BookCover :book="reading.book" :show-title="false" rounded="rounded-lg" /></span>
		<span class="flex min-w-0 flex-col gap-0.5">
			<span class="line-clamp-2 type-title-small">{{ reading.book.title }}</span>
			<span class="type-title-medium-em">{{
				reading.physical ? s.stats.pagesRead(reading.pages) : formatDuration(reading.durationMs)
			}}</span>
			<span v-for="line in lines" :key="line" class="type-body-small text-on-surface-variant">{{ line }}</span>
		</span>
	</RouterLink>
</template>
