<script setup lang="ts">
import { computed } from 'vue';
import EmptyState from '@/components/EmptyState.vue';
import GoalRing from '@/components/GoalRing.vue';
import { formatDuration, formatTime, isUiBirthday } from '@/lib/format';
import type { DayStats } from '@/lib/stats';
import { s } from '@/strings';
import BookReadingCard from './BookReadingCard.vue';
import CompletedRow from './CompletedRow.vue';
import WeekStrip from './WeekStrip.vue';

/** One day, as the phone's `StatisticsDay`: the week strip, the day against its goal, then what was read and when. */
const props = defineProps<{ stats: DayStats; today: number }>();
const emit = defineEmits<{ pick: [day: number] }>();
const progress = computed(() => (props.stats.goalMinutes > 0 ? props.stats.durationMs / 60_000 / props.stats.goalMinutes : 0));
const empty = computed(
	() => props.stats.durationMs === 0 && props.stats.pages === 0 && props.stats.books.length === 0 && props.stats.completed.length === 0,
);
// A day read on paper took no time on record, so its pages are its figure.
const headline = computed(() =>
	props.stats.durationMs === 0 && props.stats.pages > 0 ? s.stats.pagesRead(props.stats.pages) : formatDuration(props.stats.durationMs),
);
</script>

<template>
	<div class="flex flex-col gap-4">
		<WeekStrip :week="stats.week" :selected="stats.day" :today="today" :goal-minutes="stats.goalMinutes" @pick="emit('pick', $event)" />
		<section class="flex items-center gap-5 rounded-xl bg-surface-container-low p-4">
			<GoalRing :progress="progress" :label="`${Math.round(progress * 100)}%`" />
			<div class="min-w-0">
				<p class="type-label-large text-on-surface-variant">{{ s.stats.goalMinutes(formatDuration(stats.goalMinutes * 60_000)) }}</p>
				<p class="type-display-small-em">{{ headline }}</p>
				<p v-if="stats.pages > 0" class="type-body-medium text-on-surface-variant">{{ s.stats.pagesRead(stats.pages) }}</p>
			</div>
		</section>
		<EmptyState
			v-if="empty"
			:face="isUiBirthday(stats.day) ? 'happy' : 'asleep'"
			icon="autoStoriesOff"
			:title="isUiBirthday(stats.day) ? s.stats.birthdayTitle : s.stats.dayEmptyTitle"
			:message="isUiBirthday(stats.day) ? s.stats.birthdayMessage : s.stats.dayEmptyMessage"
		/>
		<template v-else>
			<template v-if="stats.books.length">
				<h2 class="section-title">{{ s.stats.books }}</h2>
				<BookReadingCard v-for="reading in stats.books" :key="`${reading.book.id}${reading.physical}`" :reading="reading" />
			</template>
			<template v-if="stats.completed.length">
				<h2 class="section-title">{{ s.stats.finished }}</h2>
				<CompletedRow v-for="entry in stats.completed" :key="entry.readThroughId" :entry="entry" />
			</template>
			<template v-if="stats.timeline.length">
				<h2 class="section-title">{{ s.stats.timeline }}</h2>
				<div class="flex flex-col gap-3">
					<div v-for="(entry, index) in stats.timeline" :key="index" class="flex gap-3">
						<span class="w-18 shrink-0 type-label-large text-on-surface-variant">{{ formatTime(entry.at) }}</span>
						<span class="min-w-0">
							<span class="block truncate type-body-medium">{{ entry.book.title }}</span>
							<span class="block type-body-small text-on-surface-variant">
								{{
									entry.kind === 'session'
										? [formatDuration(entry.durationMs), entry.chapter].filter(Boolean).join(' · ')
										: `${s.book.progressPhysical} · ${s.stats.pageSpan(entry.startPage, entry.endPage)}`
								}}
							</span>
						</span>
					</div>
				</div>
			</template>
		</template>
	</div>
</template>
