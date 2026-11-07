<script setup lang="ts">
import { ref, useId } from 'vue';

export type Face =
	'happy' | 'content' | 'neutral' | 'curious' | 'studious' | 'asleep' | 'downcast' | 'worried' | 'dismayed' | 'dumbfounded' | 'shocked';

/**
 * Ui from the shoulders up, in front of a plate of the theme's colour, as the phone's `Portrait`.
 * The plate cuts her off below, while her head stands clear of it above and her halo rises out of
 * its top, so this is taller than the plate, which is as wide as it is: 128px unless told.
 * Pressing and holding on her pats her: she is happy until let go.
 */
withDefaults(defineProps<{ face: Face; width?: number }>(), { width: 128 });

// The artwork's own size: the plate is the square at its foot.
const W = 432;
const H = 546;
const TOP = H - W;

/** Material's Cookie9Sided, near enough: nine soft lobes, one at the top. */
const plate = (() => {
	const points: string[] = [];
	const steps = 180;
	for (let step = 0; step < steps; step++) {
		const angle = (step / steps) * Math.PI * 2;
		const radius = (W / 2) * (0.9 + 0.1 * Math.cos(9 * angle));
		const x = W / 2 + radius * Math.sin(angle);
		const y = TOP + W / 2 - radius * Math.cos(angle);
		points.push(`${step === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`);
	}
	return `${points.join('')}Z`;
})();

const id = useId();
const patted = ref(false);
</script>

<template>
	<svg
		:width="width"
		:height="(width * H) / W"
		:viewBox="`0 0 ${W} ${H}`"
		class="shrink-0 select-none"
		aria-hidden="true"
		@pointerdown="patted = true"
		@pointerup="patted = false"
		@pointerleave="patted = false"
		@pointercancel="patted = false"
	>
		<clipPath :id="id">
			<path :d="plate" />
			<rect x="0" y="0" :width="W" :height="TOP + W / 2" />
		</clipPath>
		<path :d="plate" class="fill-primary-container" />
		<image :href="`/ui/${patted ? 'happy' : face}.webp`" x="0" y="0" :width="W" :height="H" :clip-path="`url(#${id})`" />
	</svg>
</template>
