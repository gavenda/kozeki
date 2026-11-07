<script setup lang="ts">
import { computed } from 'vue';

/**
 * Material's CircularWavyProgressIndicator: the done part a wavy line, the rest a flat track, with
 * a gap between them. [size] and the line are in pixels; the figure in the middle is the slot.
 */
const props = withDefaults(
	defineProps<{
		progress: number;
		size: number;
		stroke?: number;
		gap?: number;
		wavelength?: number;
		amplitude?: number;
		tone?: 'primary' | 'tertiary';
	}>(),
	{ stroke: 4, gap: 4, wavelength: 15, amplitude: 1.6, tone: 'tertiary' },
);

const shape = computed(() => {
	const { size, stroke, gap, wavelength, amplitude } = props;
	const progress = Math.min(Math.max(props.progress, 0), 1);
	const radius = size / 2 - stroke / 2 - amplitude;
	const circumference = 2 * Math.PI * radius;
	// A whole number of waves around, so the line closes on itself at full.
	const waves = Math.max(3, Math.round(circumference / wavelength));
	const point = (angle: number, wavy: boolean) => {
		const r = radius + (wavy ? amplitude * Math.sin(angle * waves) : 0);
		return `${(size / 2 + r * Math.sin(angle)).toFixed(2)} ${(size / 2 - r * Math.cos(angle)).toFixed(2)}`;
	};
	const arc = (from: number, to: number, wavy: boolean) => {
		if (to <= from) return '';
		const steps = Math.max(2, Math.ceil(((to - from) / (Math.PI * 2)) * waves * 8));
		return Array.from({ length: steps + 1 }, (_, step) => `${step ? 'L' : 'M'}${point(from + ((to - from) * step) / steps, wavy)}`).join(
			'',
		);
	};
	const gapAngle = progress > 0 && progress < 1 ? (gap + stroke) / radius : 0;
	const done = progress * Math.PI * 2;
	return {
		active: progress > 0 ? arc(0, done, true) : '',
		track: progress < 1 ? arc(done + gapAngle, Math.PI * 2 - gapAngle, false) : '',
	};
});
</script>

<template>
	<div class="relative inline-flex shrink-0 items-center justify-center" :style="{ width: `${size}px`, height: `${size}px` }">
		<svg class="absolute inset-0" :width="size" :height="size" aria-hidden="true" fill="none" stroke-linecap="round">
			<path
				:d="shape.track"
				:class="tone === 'primary' ? 'stroke-secondary-container' : 'stroke-tertiary-container'"
				:stroke-width="stroke"
			/>
			<path :d="shape.active" :class="tone === 'primary' ? 'stroke-primary' : 'stroke-tertiary'" :stroke-width="stroke" />
		</svg>
		<slot />
	</div>
</template>
