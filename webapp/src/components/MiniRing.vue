<script setup lang="ts">
import { computed } from 'vue';

/** A small plain ring for a strip of days, with its figure in the middle, as the phone's `MiniRing`. */
const props = withDefaults(defineProps<{ progress: number; size?: number; onTint?: boolean }>(), { size: 36 });
const radius = computed(() => (props.size - 3) / 2);
const length = computed(() => 2 * Math.PI * radius.value);
</script>

<template>
	<div class="relative inline-flex shrink-0 items-center justify-center" :style="{ width: `${size}px`, height: `${size}px` }">
		<svg class="absolute inset-0 -rotate-90" :width="size" :height="size" aria-hidden="true" fill="none" stroke-width="3">
			<circle
				:cx="size / 2"
				:cy="size / 2"
				:r="radius"
				:class="onTint ? 'stroke-on-secondary-container/30' : 'stroke-tertiary-container'"
			/>
			<circle
				:cx="size / 2"
				:cy="size / 2"
				:r="radius"
				:class="onTint ? 'stroke-on-secondary-container' : 'stroke-tertiary'"
				:stroke-dasharray="length"
				:stroke-dashoffset="length * (1 - Math.min(Math.max(progress, 0), 1))"
			/>
		</svg>
		<slot />
	</div>
</template>
