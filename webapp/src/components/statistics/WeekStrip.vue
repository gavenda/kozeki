<script setup lang="ts">
import MiniRing from '@/components/MiniRing.vue';
import { dateOfEpochDay } from '@/lib/dates';
import type { DayReading } from '@/lib/stats';
import { s } from '@/strings';

/** The week around the chosen day, a ring for each against the daily goal, as the phone's `WeekStrip`. Days to come are not open yet. */
defineProps<{ week: DayReading[]; selected: number; today: number; goalMinutes: number }>();
const emit = defineEmits<{ pick: [day: number] }>();
const weekday = (day: number) => dateOfEpochDay(day).toLocaleDateString(undefined, { weekday: 'short' });
const date = (day: number) => dateOfEpochDay(day).toLocaleDateString(undefined, { dateStyle: 'medium' });
</script>

<template>
	<div class="flex justify-between">
		<button
			v-for="entry in week"
			:key="entry.day"
			class="flex cursor-pointer flex-col items-center gap-1.5 rounded-2xl px-1 py-2 transition-colors disabled:cursor-default disabled:opacity-40"
			:class="entry.day === selected ? 'bg-secondary-container text-on-secondary-container' : 'hover:bg-on-surface/8'"
			:disabled="entry.day > today"
			:aria-label="s.stats.dayDescription(weekday(entry.day), date(entry.day))"
			:aria-pressed="entry.day === selected"
			@click="emit('pick', entry.day)"
		>
			<span class="type-label-small" :class="entry.day === selected ? '' : 'text-on-surface-variant'">{{ weekday(entry.day) }}</span>
			<MiniRing :progress="goalMinutes > 0 ? entry.durationMs / 60_000 / goalMinutes : 0" :on-tint="entry.day === selected">
				<span class="type-label-medium">{{ dateOfEpochDay(entry.day).getDate() }}</span>
			</MiniRing>
		</button>
	</div>
</template>
