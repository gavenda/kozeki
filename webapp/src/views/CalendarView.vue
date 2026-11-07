<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import EmptyState from '@/components/EmptyState.vue';
import { epochDayOfDate, today } from '@/lib/dates';
import { formatDuration } from '@/lib/format';
import { calendarDays } from '@/lib/stats';
import { useStatsInput } from '@/lib/statsInput';
import { s } from '@/strings';

const input = useStatsInput();
const now = new Date();
const month = ref({ year: now.getFullYear(), month: now.getMonth() });

const first = computed(() => epochDayOfDate(month.value.year, month.value.month, 1));
const next = computed(() => epochDayOfDate(month.value.year, month.value.month + 1, 1));
const days = computed(() => calendarDays(input.value, first.value, next.value));
const title = computed(() =>
	new Date(month.value.year, month.value.month, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
);

// Weeks start on the browser's locale's first day where it says, Monday otherwise.
const weekStart = (() => {
	const info = new Intl.Locale(navigator.language) as Intl.Locale & {
		weekInfo?: { firstDay: number };
		getWeekInfo?: () => { firstDay: number };
	};
	return (info.getWeekInfo?.() ?? info.weekInfo)?.firstDay ?? 1;
})();
const weekdays = computed(() =>
	Array.from({ length: 7 }, (_, index) =>
		new Date(2024, 0, (weekStart + index) % 7 || 7).toLocaleDateString(undefined, { weekday: 'short' }),
	),
);
const blanks = computed(() => (new Date(month.value.year, month.value.month, 1).getDay() - (weekStart % 7) + 7) % 7);
const length = computed(() => next.value - first.value);

function shift(by: number) {
	const date = new Date(month.value.year, month.value.month + by, 1);
	month.value = { year: date.getFullYear(), month: date.getMonth() };
}

const selected = ref<number | null>(null);
const selectedBooks = computed(() => (selected.value == null ? [] : (days.value.get(selected.value) ?? [])));
</script>

<template>
	<div class="flex flex-col gap-4">
		<div class="flex items-center gap-2">
			<h1 class="flex-1 text-2xl font-medium">{{ title }}</h1>
			<button class="icon-btn" :aria-label="s.calendar.previous" @click="shift(-1)"><AppIcon name="arrowBack" /></button>
			<button class="icon-btn rotate-180" :aria-label="s.calendar.next" @click="shift(1)"><AppIcon name="arrowBack" /></button>
		</div>

		<div class="grid grid-cols-7 gap-1 text-center text-xs text-on-surface-variant">
			<span v-for="day in weekdays" :key="day" class="py-1">{{ day }}</span>
		</div>
		<div class="grid grid-cols-7 gap-1">
			<span v-for="blank in blanks" :key="`b${blank}`" />
			<button
				v-for="index in length"
				:key="index"
				class="relative aspect-[2/3] overflow-hidden rounded-lg bg-surface-container text-left"
				:class="{ 'ring-2 ring-primary': first + index - 1 === today(), 'outline-2 outline-secondary': selected === first + index - 1 }"
				@click="selected = first + index - 1"
			>
				<template v-if="days.get(first + index - 1)?.length">
					<div class="absolute inset-0"><BookCover :book="days.get(first + index - 1)![0].book" /></div>
					<span
						v-if="days.get(first + index - 1)!.length > 1"
						class="absolute right-1 bottom-1 rounded-md bg-scrim/60 px-1 text-[10px] text-white"
					>
						+{{ days.get(first + index - 1)!.length - 1 }}
					</span>
				</template>
				<span class="absolute top-1 left-1.5 rounded bg-surface/70 px-1 text-xs font-medium">{{ index }}</span>
			</button>
		</div>

		<EmptyState v-if="days.size === 0" face="asleep" :title="s.calendar.nothing" />

		<ul v-if="selectedBooks.length" class="flex flex-col gap-2">
			<li v-for="entry in selectedBooks" :key="entry.book.id">
				<RouterLink :to="{ name: 'book', params: { id: entry.book.id } }" class="card flex items-center gap-4 p-3">
					<div class="w-12 shrink-0"><BookCover :book="entry.book" /></div>
					<div class="min-w-0 flex-1">
						<p class="truncate font-medium">{{ entry.book.title }}</p>
						<p class="text-sm text-on-surface-variant">
							{{
								[
									entry.durationMs ? formatDuration(entry.durationMs) : null,
									entry.onPaper ? s.calendar.onPaper : null,
									entry.completed ? s.calendar.finished : null,
								]
									.filter(Boolean)
									.join(' · ')
							}}
						</p>
					</div>
				</RouterLink>
			</li>
		</ul>
	</div>
</template>
