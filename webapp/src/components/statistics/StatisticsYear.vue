<script setup lang="ts">
import { computed } from 'vue';
import ChartCard from '@/components/ChartCard.vue';
import ColumnChart from '@/components/ColumnChart.vue';
import GoalRing from '@/components/GoalRing.vue';
import StatTile from '@/components/StatTile.vue';
import { formatDuration, formatPrice } from '@/lib/format';
import type { YearStats } from '@/lib/stats';
import { s } from '@/strings';
import CompletedRow from './CompletedRow.vue';
import LongestBooksCard from './LongestBooksCard.vue';
import RatingsCard from './RatingsCard.vue';
import SpendingCard from './SpendingCard.vue';

/** A year, as the phone's `StatisticsYear`: the goal, the totals, a column a month, ratings, the longest books, spending, the books finished. */
const props = defineProps<{ stats: YearStats; currentYear: number; currentMonth: number }>();
const emit = defineEmits<{ setGoal: [] }>();

const initials = Array.from({ length: 12 }, (_, month) => new Date(2024, month, 1).toLocaleDateString(undefined, { month: 'narrow' }));
const months = computed(() => (props.stats.year === props.currentYear ? props.currentMonth + 1 : 12));
const books = computed(() =>
	props.stats.completedPerMonth.map((count, month) => ({ label: initials[month], value: count, valueText: s.bookCount(count) })),
);
const time = computed(() =>
	props.stats.durationPerMonth.map((ms, month) => ({ label: initials[month], value: ms / 3_600_000, valueText: formatDuration(ms) })),
);
const spending = computed(() =>
	props.stats.spendingPerMonth.map((minor, month) => ({
		label: initials[month],
		value: minor,
		valueText: formatPrice(minor, props.stats.spendingCurrency || undefined),
	})),
);
const goalProgress = computed(() => (props.stats.goalBooks ? props.stats.completed.length / props.stats.goalBooks : 0));
</script>

<template>
	<div class="flex flex-col gap-4">
		<section class="flex items-center gap-5 rounded-xl bg-surface-container-low p-4">
			<template v-if="stats.goalBooks">
				<GoalRing :progress="goalProgress" :label="`${Math.round(goalProgress * 100)}%`" />
				<div>
					<p class="type-label-large text-on-surface-variant">{{ s.stats.goalBooks(stats.goalBooks) }}</p>
					<p class="type-headline-medium-em">{{ s.stats.booksFinished(stats.completed.length) }}</p>
				</div>
			</template>
			<div v-else class="flex flex-1 flex-wrap items-center gap-3">
				<div class="flex-1">
					<p class="type-headline-medium-em">{{ s.stats.booksFinished(stats.completed.length) }}</p>
					<p class="type-body-medium text-on-surface-variant">{{ s.stats.noGoal(stats.year) }}</p>
				</div>
				<button class="btn bg-secondary text-on-secondary hover:brightness-110" @click="emit('setGoal')">{{ s.stats.setGoal }}</button>
			</div>
		</section>
		<div class="flex flex-col gap-2">
			<div class="flex gap-2">
				<StatTile :label="s.stats.monthlyAverage" :value="s.stats.booksDecimal(stats.completed.length / months)" />
				<StatTile :label="s.stats.pages" :value="stats.pages.toLocaleString()" />
			</div>
			<StatTile :label="s.stats.timeRead" :value="formatDuration(stats.durationMs)" />
		</div>
		<ChartCard :title="s.stats.booksPerMonth">
			<ColumnChart
				:bars="books"
				:description="`${s.stats.booksPerMonth}: ${books.map((bar) => `${bar.label} ${bar.valueText}`).join('; ')}`"
				:axis-text="(value) => String(value)"
				whole-numbers
			/>
		</ChartCard>
		<ChartCard :title="s.stats.timePerMonth">
			<ColumnChart
				:bars="time"
				:description="`${s.stats.timePerMonth}: ${time.map((bar) => `${bar.label} ${bar.valueText}`).join('; ')}`"
				:axis-text="(value) => s.stats.hours(value)"
				whole-numbers
			/>
		</ChartCard>
		<RatingsCard v-if="stats.ratingAverage != null" :average="stats.ratingAverage" :counts="stats.ratingCounts" />
		<LongestBooksCard v-if="stats.completed.length" :completed="stats.completed" />
		<SpendingCard v-if="stats.spending.size" :spending="stats.spending" :bars="spending" :currency="stats.spendingCurrency" />
		<template v-if="stats.completed.length">
			<h2 class="section-title">{{ s.stats.finished }}</h2>
			<CompletedRow v-for="entry in [...stats.completed].reverse()" :key="entry.readThroughId" :entry="entry" />
		</template>
	</div>
</template>
