<script setup lang="ts">
import { computed } from 'vue';
import ChartCard from '@/components/ChartCard.vue';
import ColumnChart from '@/components/ColumnChart.vue';
import StatTile from '@/components/StatTile.vue';
import { dateOfEpochDay } from '@/lib/dates';
import { formatDuration } from '@/lib/format';
import type { PeriodStats } from '@/lib/stats';
import { s } from '@/strings';
import BookReadingCard from './BookReadingCard.vue';
import CompletedRow from './CompletedRow.vue';

/** A week or a month, as the phone's `StatisticsPeriod`: the totals, a column for each day against the goal, then the books. */
const props = defineProps<{ stats: PeriodStats; month: boolean }>();
const bars = computed(() =>
	props.stats.days.map((day) => {
		const date = dateOfEpochDay(day.day);
		const label = props.month
			? (date.getDate() - 1) % 7 === 0
				? String(date.getDate())
				: ''
			: date.toLocaleDateString(undefined, { weekday: 'narrow' });
		return { label, value: day.durationMs / 60_000, valueText: formatDuration(day.durationMs) };
	}),
);
</script>

<template>
	<div class="flex flex-col gap-4">
		<div class="flex flex-col gap-2">
			<div class="flex gap-2">
				<StatTile :label="s.stats.timeRead" :value="formatDuration(stats.durationMs)" />
				<StatTile :label="s.stats.pages" :value="stats.pages.toLocaleString()" />
			</div>
			<div class="flex gap-2">
				<StatTile :label="s.stats.daysRead" :value="s.xOfY(stats.daysRead, stats.days.length)" />
				<StatTile :label="s.stats.goalReached" :value="s.stats.days(stats.goalDaysMet)" />
			</div>
		</div>
		<ChartCard :title="s.stats.timePerDay">
			<ColumnChart
				:bars="bars"
				:description="`${s.stats.timePerDay}: ${bars.map((bar) => bar.valueText).join('; ')}`"
				:axis-text="(value) => s.stats.minutes(Math.round(value))"
				:reference="{ value: stats.goalMinutes, label: s.stats.goal }"
			/>
		</ChartCard>
		<template v-if="stats.books.length">
			<h2 class="section-title">{{ s.stats.books }}</h2>
			<BookReadingCard v-for="reading in stats.books" :key="`${reading.book.id}${reading.physical}`" :reading="reading" />
		</template>
		<template v-if="stats.completed.length">
			<h2 class="section-title">{{ s.stats.finished }}</h2>
			<CompletedRow v-for="entry in stats.completed" :key="entry.readThroughId" :entry="entry" />
		</template>
	</div>
</template>
