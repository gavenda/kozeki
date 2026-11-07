<script setup lang="ts">
import HaloMark from './HaloMark.vue';
import WavyRing from './WavyRing.vue';

/**
 * A ratio against a goal, with the figure in the middle, as the phone's `GoalRing`. The ring stops
 * at full; the figure does not. A goal that is met becomes Ui's halo: its outer ring lands on the
 * full one, and the ring inside and the ties between the two arrive.
 */
withDefaults(defineProps<{ progress: number; label: string; size?: number }>(), { size: 96 });
</script>

<template>
	<WavyRing :progress="progress" :size="size" :stroke="size / 12" :amplitude="size / 60" :wavelength="size / 4">
		<HaloMark
			:size="size"
			:dial="false"
			class="absolute inset-0 transition-opacity duration-400"
			:class="progress >= 1 ? 'opacity-100' : 'opacity-0'"
		/>
		<span class="relative type-title-medium-em">{{ label }}</span>
	</WavyRing>
</template>
