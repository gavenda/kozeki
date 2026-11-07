<script setup lang="ts">
import { epochDayOf } from '@/lib/dates';
import { formatDay } from '@/lib/format';
import { s } from '@/strings';
import type { ReadThrough } from '@/types';

/** Each time the book was read, as the phone's `HistorySection`: when it started and how it ended. */
defineProps<{ readThroughs: ReadThrough[] }>();
const ending = (row: ReadThrough) => {
	if (row.finishedOn == null) return s.history.started(formatDay(epochDayOf(row.startedAt)));
	return row.outcome === 'DROPPED' ? s.history.dropped(formatDay(row.finishedOn)) : s.history.completed(formatDay(row.finishedOn));
};
</script>

<template>
	<section class="flex flex-col gap-2">
		<h2 class="section-title">{{ s.history.title }}</h2>
		<div v-for="row in readThroughs" :key="row.id" class="flex type-body-medium">
			<span class="w-28 shrink-0">{{ s.history.reading(row.number) }}</span>
			<span class="text-on-surface-variant">
				{{ row.finishedOn == null ? ending(row) : `${s.history.started(formatDay(epochDayOf(row.startedAt)))} · ${ending(row)}` }}
			</span>
		</div>
	</section>
</template>
