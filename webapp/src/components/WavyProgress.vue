<script setup lang="ts">
import { useElementSize } from '@vueuse/core';
import { computed, ref } from 'vue';

/**
 * Material's LinearWavyProgressIndicator: the done part a wavy line, then a gap and a flat track
 * with a stop at the end. [tone] picks the colour: primary for the EPUB, tertiary for paper.
 */
const props = withDefaults(defineProps<{ progress: number; tone?: 'primary' | 'tertiary' }>(), { tone: 'primary' });

const box = ref<HTMLElement>();
const { width } = useElementSize(box);
const HEIGHT = 10;
const STROKE = 4;
const WAVELENGTH = 40;
const AMPLITUDE = 3;

const shape = computed(() => {
	const total = width.value;
	const progress = Math.min(Math.max(props.progress, 0), 1);
	const end = STROKE / 2 + (total - STROKE) * progress;
	const middle = HEIGHT / 2;
	const steps = Math.max(2, Math.ceil(end / 4));
	const wave = Array.from({ length: steps + 1 }, (_, step) => {
		const x = STROKE / 2 + ((end - STROKE / 2) * step) / steps;
		return `${step ? 'L' : 'M'}${x.toFixed(2)} ${(middle + AMPLITUDE * Math.sin((x / WAVELENGTH) * Math.PI * 2)).toFixed(2)}`;
	}).join('');
	const trackStart = progress > 0 ? end + STROKE + 4 : STROKE / 2;
	return {
		wave: progress > 0 ? wave : '',
		track: trackStart < total - STROKE / 2 ? `M${trackStart} ${middle}H${total - STROKE / 2}` : '',
		stop: total - STROKE / 2,
	};
});
</script>

<template>
	<div ref="box" class="h-2.5 w-full" role="progressbar" :aria-valuenow="Math.round(progress * 100)" aria-valuemin="0" aria-valuemax="100">
		<svg v-if="width" :width="width" :height="HEIGHT" fill="none" stroke-linecap="round">
			<path
				:d="shape.track"
				:class="tone === 'primary' ? 'stroke-primary-container' : 'stroke-tertiary-container'"
				:stroke-width="STROKE"
			/>
			<path :d="shape.wave" :class="tone === 'primary' ? 'stroke-primary' : 'stroke-tertiary'" :stroke-width="STROKE" />
			<circle v-if="progress < 1" :cx="shape.stop" :cy="HEIGHT / 2" r="2" :class="tone === 'primary' ? 'fill-primary' : 'fill-tertiary'" />
		</svg>
	</div>
</template>
