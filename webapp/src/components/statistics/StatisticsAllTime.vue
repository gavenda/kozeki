<script setup lang="ts">
import { computed } from 'vue';
import ChartCard from '@/components/ChartCard.vue';
import ColumnChart from '@/components/ColumnChart.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import StatTile from '@/components/StatTile.vue';
import { formatDay, formatDuration, formatPrice } from '@/lib/format';
import type { AllTimeStats } from '@/lib/stats';
import { s } from '@/strings';
import CompletedRow from './CompletedRow.vue';
import LongestBooksCard from './LongestBooksCard.vue';
import RatingsCard from './RatingsCard.vue';
import SpendingCard from './SpendingCard.vue';

/** Everything on record, as the phone's `StatisticsAllTime`: a column a year, and the guest book's first page. */
const props = defineProps<{ stats: AllTimeStats }>();

/** About six years are named, the current one always. */
const label = (index: number, count: number, year: number) => {
	const step = Math.max(1, Math.ceil(count / 6));
	return (count - 1 - index) % step === 0 ? String(year) : '';
};
const books = computed(() =>
	props.stats.years.map((entry, index, all) => ({
		label: label(index, all.length, entry.year),
		value: entry.completed,
		valueText: s.bookCount(entry.completed),
	})),
);
const time = computed(() =>
	props.stats.years.map((entry, index, all) => ({
		label: label(index, all.length, entry.year),
		value: entry.durationMs / 3_600_000,
		valueText: formatDuration(entry.durationMs),
	})),
);
const spending = computed(() =>
	props.stats.spendingPerYear.map((entry, index, all) => ({
		label: label(index, all.length, entry.year),
		value: entry.minor,
		valueText: formatPrice(entry.minor, props.stats.spendingCurrency || undefined),
	})),
);
const empty = computed(() => props.stats.since == null && props.stats.spending.size === 0);
</script>

<template>
	<EmptyState v-if="empty" face="content" icon="autoStoriesOff" :title="s.stats.allTimeEmptyTitle" :message="s.stats.allTimeEmptyMessage" />
	<div v-else class="flex flex-col gap-4">
		<section class="relative overflow-hidden rounded-xl bg-tertiary-container p-4 text-on-tertiary-container">
			<HaloMark :size="200" :dial="false" class="absolute -end-16 top-1/2 -translate-y-1/2" />
			<div class="relative">
				<p v-if="stats.since != null" class="type-label-large">{{ s.stats.since(formatDay(stats.since)) }}</p>
				<p class="type-headline-medium-em">{{ s.stats.booksFinished(stats.completed.length) }}</p>
			</div>
		</section>
		<div class="flex flex-col gap-2">
			<div class="flex gap-2">
				<StatTile :label="s.stats.timeRead" :value="formatDuration(stats.durationMs)" />
				<StatTile :label="s.stats.pages" :value="stats.pages.toLocaleString()" />
			</div>
			<div class="flex gap-2">
				<StatTile :label="s.stats.daysRead" :value="stats.daysRead.toLocaleString()" />
				<StatTile :label="s.stats.monthlyAverage" :value="s.stats.booksDecimal(stats.booksPerMonth)" />
			</div>
		</div>
		<template v-if="stats.years.length">
			<ChartCard :title="s.stats.booksPerYear">
				<ColumnChart
					:bars="books"
					:description="`${s.stats.booksPerYear}: ${books.map((bar) => bar.valueText).join('; ')}`"
					:axis-text="(value) => String(value)"
					whole-numbers
				/>
			</ChartCard>
			<ChartCard :title="s.stats.timePerYear">
				<ColumnChart
					:bars="time"
					:description="`${s.stats.timePerYear}: ${time.map((bar) => bar.valueText).join('; ')}`"
					:axis-text="(value) => s.stats.hours(value)"
					whole-numbers
				/>
			</ChartCard>
		</template>
		<RatingsCard v-if="stats.ratingAverage != null" :average="stats.ratingAverage" :counts="stats.ratingCounts" />
		<LongestBooksCard v-if="stats.completed.length" :completed="stats.completed" />
		<SpendingCard v-if="stats.spending.size" :spending="stats.spending" :bars="spending" :currency="stats.spendingCurrency" />
		<template v-if="stats.completed.length">
			<h2 class="section-title">{{ s.stats.finished }}</h2>
			<CompletedRow v-for="entry in [...stats.completed].reverse()" :key="entry.readThroughId" :entry="entry" />
		</template>
	</div>
</template>
