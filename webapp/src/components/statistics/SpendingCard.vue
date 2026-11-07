<script setup lang="ts">
import ChartCard from '@/components/ChartCard.vue';
import ColumnChart, { type ChartBar } from '@/components/ColumnChart.vue';
import { formatPrice } from '@/lib/format';
import { s } from '@/strings';

/** What was spent on books, a total for each currency, and a chart of it in the one most used, as the phone's `SpendingCard`. */
defineProps<{ spending: Map<string, number>; bars: ChartBar[]; currency: string | null }>();
</script>

<template>
	<ChartCard :title="s.stats.spending">
		<p v-for="[code, minor] in spending" :key="code" class="type-headline-medium-em">{{ formatPrice(minor, code || undefined) }}</p>
		<ColumnChart
			v-if="bars.some((bar) => bar.value > 0)"
			:bars="bars"
			:description="`${s.stats.spending}: ${bars.map((bar) => `${bar.label} ${bar.valueText}`).join('; ')}`"
			:axis-text="(value) => formatPrice(value, currency || undefined)"
		/>
	</ChartCard>
</template>
