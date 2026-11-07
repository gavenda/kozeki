<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ButtonGroup from '@/components/ButtonGroup.vue';
import SettingsButton from '@/components/SettingsButton.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import YearlyGoalDialog from '@/components/YearlyGoalDialog.vue';
import PeriodNavigator from '@/components/statistics/PeriodNavigator.vue';
import StatisticsAllTime from '@/components/statistics/StatisticsAllTime.vue';
import StatisticsDay from '@/components/statistics/StatisticsDay.vue';
import StatisticsPeriod from '@/components/statistics/StatisticsPeriod.vue';
import StatisticsYear from '@/components/statistics/StatisticsYear.vue';
import { dateOfEpochDay, epochDayOfDate, epochDayOfIso, firstDayOfWeek, isoOfEpochDay, today as todayOf } from '@/lib/dates';
import { formatDay, formatMonth } from '@/lib/format';
import * as ops from '@/lib/library';
import type { OptionColor } from '@/lib/optionColor';
import { allTimeStats, dayStats, periodStats, weekStartOf, yearStats } from '@/lib/stats';
import { useStatsInput } from '@/lib/statsInput';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

/**
 * The statistics, as the phone's `StatisticsScreen`: a day, a week, a month, a year or all of it,
 * stepped through one at a time. Opened on a day from the calendar ([date]), it is a page of its
 * own, with a way back. The range and the day it is on are in the address, so Back finds them.
 */
const props = defineProps<{ date?: string }>();
const route = useRoute();
const router = useRouter();
const library = useLibrary();
const input = useStatsInput();

type Range = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'ALL';
const RANGES: { value: Range; label: string; color: OptionColor }[] = [
	{ value: 'DAY', label: s.stats.range.DAY, color: 'violet' },
	{ value: 'WEEK', label: s.stats.range.WEEK, color: 'gold' },
	{ value: 'MONTH', label: s.stats.range.MONTH, color: 'gold' },
	{ value: 'YEAR', label: s.stats.range.YEAR, color: 'gold' },
	{ value: 'ALL', label: s.stats.range.ALL, color: 'leather' },
];

const today = todayOf();
const weekStart = firstDayOfWeek();
const detail = computed(() => props.date != null);
const range = computed<Range>(() => {
	const asked = route.query.range as Range | undefined;
	return RANGES.some((option) => option.value === asked) ? asked! : 'DAY';
});
const anchor = computed(() => {
	const asked = typeof route.query.d === 'string' ? epochDayOfIso(route.query.d) : null;
	return Math.min(asked ?? (props.date ? epochDayOfIso(props.date) : null) ?? today, today);
});

function go(next: { range?: Range; day?: number }) {
	const query = { ...route.query, range: next.range ?? range.value, d: isoOfEpochDay(next.day ?? anchor.value) };
	void router.replace({ query });
}

const dateOf = (day: number) => dateOfEpochDay(day);
const period = computed(() => {
	const day = anchor.value;
	const date = dateOf(day);
	switch (range.value) {
		case 'DAY':
			return { start: day, end: day };
		case 'WEEK': {
			const start = weekStartOf(day, weekStart);
			return { start, end: start + 6 };
		}
		case 'MONTH': {
			const start = epochDayOfDate(date.getFullYear(), date.getMonth(), 1);
			return { start, end: epochDayOfDate(date.getFullYear(), date.getMonth() + 1, 1) - 1 };
		}
		default: {
			const start = epochDayOfDate(date.getFullYear(), 0, 1);
			return { start, end: epochDayOfDate(date.getFullYear() + 1, 0, 1) - 1 };
		}
	}
});
const canGoForward = computed(() => period.value.end < today);
function step(by: number) {
	const date = dateOf(anchor.value);
	const day =
		range.value === 'DAY'
			? anchor.value + by
			: range.value === 'WEEK'
				? anchor.value + 7 * by
				: range.value === 'MONTH'
					? epochDayOfDate(date.getFullYear(), date.getMonth() + by, 1)
					: epochDayOfDate(date.getFullYear() + by, 0, 1);
	go({ day: Math.min(day, today) });
}

const shortDate = (day: number) => dateOf(day).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
const label = computed(() => {
	const date = dateOf(anchor.value);
	switch (range.value) {
		case 'DAY':
			return anchor.value === today ? s.stats.today : formatDay(anchor.value);
		case 'WEEK':
			return s.stats.dateRange(shortDate(period.value.start), shortDate(period.value.end));
		case 'MONTH':
			return formatMonth(date.getFullYear(), date.getMonth());
		default:
			return String(date.getFullYear());
	}
});

const goalMinutes = computed(() => library.setting<number>('dailyGoalMinutes', 20));
const year = computed(() => dateOf(anchor.value).getFullYear());
const yearGoal = computed(() => {
	const row = library.tables.yearlyGoals.get(String(year.value));
	return row && ops.alive(row) ? row.books : null;
});

const day = computed(() => (range.value === 'DAY' ? dayStats(input.value, anchor.value, weekStart, goalMinutes.value) : null));
const periodData = computed(() =>
	range.value === 'WEEK' || range.value === 'MONTH'
		? periodStats(input.value, period.value.start, period.value.end, goalMinutes.value)
		: null,
);
const yearly = computed(() => (range.value === 'YEAR' ? yearStats(input.value, year.value, yearGoal.value) : null));
const allTime = computed(() => (range.value === 'ALL' ? allTimeStats(input.value, today) : null));
const color = computed(() => RANGES.find((option) => option.value === range.value)!.color);
const settingGoal = ref(false);
</script>

<template>
	<TopAppBar v-if="detail" :title="s.nav.statistics" :back="{ name: 'calendar' }" always-title />
	<TopAppBar v-else :title="s.nav.statistics" large>
		<SettingsButton />
	</TopAppBar>

	<div class="mx-auto flex max-w-[840px] flex-col gap-4 px-4 pb-8">
		<div class="flex flex-col gap-3">
			<ButtonGroup :model-value="range" :options="RANGES" @update:model-value="go({ range: $event })" />
			<PeriodNavigator
				v-if="range !== 'ALL'"
				:label="label"
				:color="color"
				:can-go-forward="canGoForward"
				@previous="step(-1)"
				@next="step(1)"
			/>
		</div>
		<StatisticsDay v-if="day" :stats="day" :today="today" @pick="go({ day: $event })" />
		<StatisticsPeriod v-else-if="periodData" :stats="periodData" :month="range === 'MONTH'" />
		<StatisticsYear
			v-else-if="yearly"
			:stats="yearly"
			:current-year="dateOf(today).getFullYear()"
			:current-month="dateOf(today).getMonth()"
			@set-goal="settingGoal = true"
		/>
		<StatisticsAllTime v-else-if="allTime" :stats="allTime" />
	</div>

	<YearlyGoalDialog
		v-if="settingGoal"
		:year="year"
		:books="yearGoal"
		@save="(books) => (library.edit((tx) => ops.setYearlyGoal(tx, year, books)), (settingGoal = false))"
		@close="settingGoal = false"
	/>
</template>
