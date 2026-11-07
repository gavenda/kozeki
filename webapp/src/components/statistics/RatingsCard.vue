<script setup lang="ts">
import ChartCard from '@/components/ChartCard.vue';
import RankedBars from '@/components/RankedBars.vue';
import { s } from '@/strings';

/** How the finished books were rated, five stars down to one, as the phone's `RatingsCard`. */
defineProps<{ average: number; counts: number[] }>();
</script>

<template>
	<ChartCard :title="s.stats.ratings">
		<p class="type-headline-medium-em">{{ s.stats.ratingAverage(average) }}</p>
		<RankedBars
			:bars="[5, 4, 3, 2, 1].map((star) => ({ label: s.stats.stars(star), value: counts[star - 1], valueText: String(counts[star - 1]) }))"
			:label-width="72"
		/>
	</ChartCard>
</template>
