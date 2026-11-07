<script setup lang="ts">
import WavyProgress from '@/components/WavyProgress.vue';
import { formatDuration, formatPercent } from '@/lib/format';
import { s } from '@/strings';
import type { Book } from '@/types';
import BookStat from './BookStat.vue';

/** Where the reader is in the EPUB, as the phone's `ProgressCard`; the reading itself happens on the phone. */
defineProps<{ book: Book; readingTimeMs: number; readingNumber: number }>();
</script>

<template>
	<section class="flex flex-col gap-3 rounded-xl bg-surface-container-high p-4">
		<p class="type-label-large text-primary">{{ s.book.progressEpub }}</p>
		<div class="flex items-end gap-2">
			<p class="type-display-small-em">{{ formatPercent(book.progression ?? 0) }}</p>
			<p v-if="readingNumber > 1" class="pb-1.5 type-label-large text-on-surface-variant">{{ s.book.readingNumber(readingNumber) }}</p>
		</div>
		<WavyProgress :progress="book.progression ?? 0" />
		<p v-if="book.chapterTitle" class="line-clamp-2 type-title-small">{{ book.chapterTitle }}</p>
		<div class="flex flex-wrap gap-6">
			<BookStat
				v-if="book.chapterIndex != null && book.chapterCount != null"
				:label="s.book.chapter"
				:value="s.xOfY(book.chapterIndex, book.chapterCount)"
			/>
			<BookStat
				v-if="book.position != null && book.positionCount != null"
				:label="s.book.page"
				:value="s.xOfY(book.position, book.positionCount)"
			/>
			<BookStat :label="s.book.timeRead" :value="formatDuration(readingTimeMs)" />
		</div>
	</section>
</template>
