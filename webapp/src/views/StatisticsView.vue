<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import EmptyState from '@/components/EmptyState.vue';
import { alive } from '@/lib/library';
import { formatDay, formatDuration, formatPrice } from '@/lib/format';
import { today } from '@/lib/dates';
import { allTimeStats, minutesOn, yearStats } from '@/lib/stats';
import { useStatsInput } from '@/lib/statsInput';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

const library = useLibrary();
const input = useStatsInput();
const range = ref<'year' | 'all'>('year');
const year = ref(new Date().getFullYear());

const goal = computed(() => {
	const row = library.tables.yearlyGoals.get(String(year.value));
	return row && alive(row) ? row.books : null;
});
const yearly = computed(() => yearStats(input.value, year.value, goal.value));
const allTime = computed(() => allTimeStats(input.value, today()));
const todayMinutes = computed(() => minutesOn(input.value, today()));
const dailyGoal = computed(() => library.setting<number>('dailyGoalMinutes', 20));

const monthNames = Array.from({ length: 12 }, (_, month) => new Date(2024, month, 1).toLocaleDateString(undefined, { month: 'narrow' }));
const maxOf = (values: number[]) => Math.max(1, ...values);
</script>

<template>
	<div class="flex flex-col gap-6">
		<div class="flex flex-wrap items-center gap-2">
			<div class="flex rounded-full border border-outline-variant p-1">
				<button
					v-for="option in ['year', 'all'] as const"
					:key="option"
					class="h-8 rounded-full px-4 text-sm font-medium"
					:class="range === option ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant'"
					@click="range = option"
				>
					{{ option === 'year' ? s.stats.year : s.stats.all }}
				</button>
			</div>
			<div v-if="range === 'year'" class="ml-auto flex items-center gap-1">
				<button class="icon-btn" :aria-label="s.stats.previous" @click="year--"><AppIcon name="arrowBack" /></button>
				<span class="w-14 text-center font-medium">{{ year }}</span>
				<button class="icon-btn rotate-180" :aria-label="s.stats.next" @click="year++"><AppIcon name="arrowBack" /></button>
			</div>
		</div>

		<div class="card flex items-center gap-4 p-5">
			<div class="flex-1">
				<p class="text-sm text-on-surface-variant">{{ s.stats.today }}</p>
				<p class="text-xl font-medium">{{ s.stats.todayOf(todayMinutes, dailyGoal) }}</p>
			</div>
			<div class="h-2 w-40 overflow-hidden rounded-full bg-surface-container-highest">
				<div class="h-full rounded-full bg-primary" :style="{ width: `${Math.min(100, (todayMinutes / dailyGoal) * 100)}%` }" />
			</div>
		</div>

		<template v-if="range === 'year'">
			<div class="grid grid-cols-2 gap-3 md:grid-cols-4">
				<div class="card p-4">
					<p class="text-xs text-on-surface-variant">{{ s.stats.finished }}</p>
					<p class="text-2xl font-medium">{{ yearly.completed.length }}</p>
					<p v-if="yearly.goalBooks" class="text-xs text-on-surface-variant">
						{{ s.stats.goalOf(yearly.completed.length, yearly.goalBooks) }}
					</p>
				</div>
				<div class="card p-4">
					<p class="text-xs text-on-surface-variant">{{ s.stats.timeRead }}</p>
					<p class="text-2xl font-medium">{{ formatDuration(yearly.durationMs) }}</p>
				</div>
				<div class="card p-4">
					<p class="text-xs text-on-surface-variant">{{ s.stats.pages }}</p>
					<p class="text-2xl font-medium">{{ yearly.pages.toLocaleString() }}</p>
				</div>
				<div class="card p-4">
					<p class="text-xs text-on-surface-variant">{{ s.stats.ratings }}</p>
					<p class="text-2xl font-medium">{{ yearly.ratingAverage != null ? s.stats.ratingAverage(yearly.ratingAverage) : '–' }}</p>
				</div>
			</div>

			<div v-if="yearly.goalBooks" class="card p-5">
				<p class="mb-2 text-sm text-on-surface-variant">{{ s.stats.goal }}</p>
				<div class="h-3 overflow-hidden rounded-full bg-surface-container-highest">
					<div
						class="h-full rounded-full bg-secondary"
						:style="{ width: `${Math.min(100, (yearly.completed.length / yearly.goalBooks) * 100)}%` }"
					/>
				</div>
			</div>

			<div class="grid gap-3 md:grid-cols-2">
				<div class="card p-5">
					<p class="mb-3 text-sm text-on-surface-variant">{{ s.stats.booksPerMonth }}</p>
					<div class="flex h-32 items-end gap-1.5">
						<div v-for="(count, month) in yearly.completedPerMonth" :key="month" class="flex flex-1 flex-col items-center gap-1">
							<div
								class="w-full rounded-t-md bg-primary"
								:style="{ height: `${(count / maxOf(yearly.completedPerMonth)) * 100}%` }"
								:title="String(count)"
							/>
							<span class="text-[10px] text-on-surface-variant">{{ monthNames[month] }}</span>
						</div>
					</div>
				</div>
				<div class="card p-5">
					<p class="mb-3 text-sm text-on-surface-variant">{{ s.stats.timePerMonth }}</p>
					<div class="flex h-32 items-end gap-1.5">
						<div v-for="(ms, month) in yearly.durationPerMonth" :key="month" class="flex flex-1 flex-col items-center gap-1">
							<div
								class="w-full rounded-t-md bg-tertiary"
								:style="{ height: `${(ms / maxOf(yearly.durationPerMonth)) * 100}%` }"
								:title="formatDuration(ms)"
							/>
							<span class="text-[10px] text-on-surface-variant">{{ monthNames[month] }}</span>
						</div>
					</div>
				</div>
			</div>

			<div v-if="yearly.spending.size" class="card p-5 text-sm">
				<p class="mb-1 text-on-surface-variant">{{ s.stats.spent }}</p>
				<p v-for="[currency, minor] in yearly.spending" :key="currency" class="text-lg font-medium">
					{{ formatPrice(minor, currency || undefined) }}
				</p>
			</div>

			<EmptyState v-if="yearly.completed.length === 0" face="content" :title="s.stats.emptyYearTitle" :message="s.stats.emptyYearMessage" />
			<section v-else class="flex flex-col gap-3">
				<h2 class="section-title">{{ s.stats.finished }}</h2>
				<ul class="grid grid-cols-[repeat(auto-fill,minmax(6rem,1fr))] gap-3">
					<li v-for="entry in [...yearly.completed].reverse()" :key="entry.readThroughId">
						<RouterLink
							:to="{ name: 'book', params: { id: entry.book.id } }"
							:title="`${entry.book.title} · ${formatDay(entry.finishedOn)}`"
						>
							<BookCover :book="entry.book" />
						</RouterLink>
					</li>
				</ul>
			</section>
		</template>

		<template v-else>
			<EmptyState v-if="allTime.since == null" face="content" :title="s.stats.emptyTitle" :message="s.stats.emptyMessage" />
			<template v-else>
				<p class="px-1 text-sm text-on-surface-variant">{{ s.stats.since(formatDay(allTime.since)) }}</p>
				<div class="grid grid-cols-2 gap-3 md:grid-cols-4">
					<div class="card p-4">
						<p class="text-xs text-on-surface-variant">{{ s.stats.finished }}</p>
						<p class="text-2xl font-medium">{{ allTime.completed.length }}</p>
						<p class="text-xs text-on-surface-variant">{{ allTime.booksPerMonth.toFixed(1) }} / month</p>
					</div>
					<div class="card p-4">
						<p class="text-xs text-on-surface-variant">{{ s.stats.timeRead }}</p>
						<p class="text-2xl font-medium">{{ formatDuration(allTime.durationMs) }}</p>
					</div>
					<div class="card p-4">
						<p class="text-xs text-on-surface-variant">{{ s.stats.pages }}</p>
						<p class="text-2xl font-medium">{{ allTime.pages.toLocaleString() }}</p>
					</div>
					<div class="card p-4">
						<p class="text-xs text-on-surface-variant">{{ s.stats.daysRead }}</p>
						<p class="text-2xl font-medium">{{ allTime.daysRead }}</p>
					</div>
				</div>
				<div class="card p-5">
					<ul class="flex flex-col gap-2 text-sm">
						<li v-for="entry in [...allTime.years].reverse()" :key="entry.year" class="flex items-center gap-4">
							<span class="w-12 font-medium">{{ entry.year }}</span>
							<div class="h-2 flex-1 overflow-hidden rounded-full bg-surface-container-highest">
								<div
									class="h-full rounded-full bg-primary"
									:style="{ width: `${(entry.completed / Math.max(1, ...allTime.years.map((y) => y.completed))) * 100}%` }"
								/>
							</div>
							<span class="w-28 text-right text-on-surface-variant">{{ entry.completed }} · {{ formatDuration(entry.durationMs) }}</span>
						</li>
					</ul>
				</div>
			</template>
		</template>
	</div>
</template>
