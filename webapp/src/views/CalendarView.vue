<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import SettingsButton from '@/components/SettingsButton.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import CalendarDayCell from '@/components/calendar/CalendarDayCell.vue';
import { epochDayOfDate, firstDayOfWeek, isoOfEpochDay, today as todayOf } from '@/lib/dates';
import { formatDay, formatDuration, formatMonth, isUiBirthday } from '@/lib/format';
import { calendarDays } from '@/lib/stats';
import { useStatsInput } from '@/lib/statsInput';
import { s } from '@/strings';

/**
 * The book calendar, as the phone's `CalendarScreen`: a month at a time, each day with the cover of
 * what was read on it. A day with books opens its statistics.
 */
const input = useStatsInput();
const router = useRouter();
const now = new Date();
const month = ref({ year: now.getFullYear(), month: now.getMonth() });
const today = todayOf();

const first = computed(() => epochDayOfDate(month.value.year, month.value.month, 1));
const next = computed(() => epochDayOfDate(month.value.year, month.value.month + 1, 1));
const days = computed(() => calendarDays(input.value, first.value, next.value));
const isCurrent = computed(() => month.value.year === now.getFullYear() && month.value.month === now.getMonth());

const weekStart = firstDayOfWeek();
const weekdays = Array.from({ length: 7 }, (_, index) =>
	new Date(2024, 0, 7 + ((weekStart + index) % 7)).toLocaleDateString(undefined, { weekday: 'short' }),
);
const blanks = computed(() => (new Date(month.value.year, month.value.month, 1).getDay() - weekStart + 7) % 7);
// Whole weeks only, the last ending where the month does.
const cells = computed(() => {
	const length = next.value - first.value;
	const total = Math.ceil((blanks.value + length) / 7) * 7;
	return Array.from({ length: total }, (_, index) => {
		const date = index - blanks.value + 1;
		return date >= 1 && date <= length ? first.value + date - 1 : null;
	});
});

const summary = computed(() => {
	const read = [...days.value.values()].filter((books) => books.some((entry) => entry.durationMs > 0 || entry.onPaper)).length;
	const ms = [...days.value.values()].flat().reduce((sum, entry) => sum + entry.durationMs, 0);
	if (read === 0) return s.calendar.nothingRead;
	return ms === 0 ? s.calendar.summaryUntimed(read) : s.calendar.summary(read, formatDuration(ms));
});

function shift(by: number) {
	const date = new Date(month.value.year, month.value.month + by, 1);
	month.value = { year: date.getFullYear(), month: date.getMonth() };
}
const openDay = (day: number) => router.push({ name: 'day', params: { date: isoOfEpochDay(day) } });
const describe = (day: number) =>
	s.calendar.dayDescription(formatDay(day), (days.value.get(day) ?? []).map((entry) => entry.book.title).join(', '));
</script>

<template>
	<TopAppBar :title="s.calendar.title" :subtitle="summary" large>
		<SettingsButton />
	</TopAppBar>
	<div class="mx-auto flex max-w-[720px] flex-col gap-2 px-3 pb-6">
		<div class="flex items-center gap-1">
			<h2 class="flex-1 ps-1 type-headline-small-em">{{ formatMonth(month.year, month.month) }}</h2>
			<button v-if="!isCurrent" class="btn-text" @click="month = { year: now.getFullYear(), month: now.getMonth() }">
				{{ s.stats.today }}
			</button>
			<button
				class="icon-btn-tonal w-10 bg-secondary-container text-on-secondary-container"
				:aria-label="s.calendar.previous"
				:title="s.calendar.previous"
				@click="shift(-1)"
			>
				<AppIcon name="keyboardArrowLeft" />
			</button>
			<button
				class="icon-btn-tonal w-10 bg-secondary-container text-on-secondary-container"
				:disabled="isCurrent"
				:aria-label="s.calendar.next"
				:title="s.calendar.next"
				@click="shift(1)"
			>
				<AppIcon name="keyboardArrowRight" />
			</button>
		</div>
		<div class="grid grid-cols-7 gap-1 text-center type-label-medium text-on-surface-variant">
			<span v-for="day in weekdays" :key="day">{{ day }}</span>
		</div>
		<div class="grid grid-cols-7 gap-1">
			<template v-for="(day, index) in cells" :key="index">
				<CalendarDayCell
					v-if="day != null"
					:day="day - first + 1"
					:books="days.get(day) ?? []"
					:today="day === today"
					:birthday="isUiBirthday(day)"
					:description="describe(day)"
					@open="openDay(day)"
				/>
				<span v-else />
			</template>
		</div>
	</div>
</template>
