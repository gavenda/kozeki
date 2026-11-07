<script setup lang="ts">
import { computed } from 'vue';

/** Horizontal bars for comparing a handful of named things, each labelled with its value at the tip (`RankedBars`). */
const props = withDefaults(defineProps<{ bars: { label: string; value: number; valueText: string }[]; labelWidth?: number }>(), {
	labelWidth: 104,
});
const max = computed(() => Math.max(...props.bars.map((bar) => bar.value)) || 1);
</script>

<template>
	<div class="flex flex-col gap-2.5">
		<div v-for="(bar, index) in bars" :key="index" class="flex items-center gap-2">
			<span class="truncate type-body-small text-on-surface-variant" :style="{ width: `${labelWidth}px` }" :title="bar.label">{{
				bar.label
			}}</span>
			<div class="flex-1">
				<div class="h-3 rounded-e-sm bg-tertiary" :style="{ width: `${Math.min(Math.max(bar.value / max, 0), 1) * 100}%` }" />
			</div>
			<span class="type-label-medium">{{ bar.valueText }}</span>
		</div>
	</div>
</template>
