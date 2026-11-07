<script setup lang="ts">
import { computed, useId } from 'vue';
import { icons, type IconDef, type IconName } from '@/lib/icons';

const props = defineProps<{ name: IconName; size?: number }>();
const id = useId();

const icon = computed(() => {
	const def: IconDef = icons[props.name];
	return typeof def === 'string' ? { viewBox: '0 -960 960 960', paths: [{ d: def }] } : def;
});
</script>

<template>
	<svg :width="size ?? 24" :height="size ?? 24" :viewBox="icon.viewBox" fill="currentColor" aria-hidden="true" class="shrink-0">
		<template v-for="(path, index) in icon.paths" :key="index">
			<clipPath v-if="path.clip" :id="`${id}-${index}`"><path :d="path.clip" /></clipPath>
			<path :d="path.d" :fill-rule="path.evenOdd ? 'evenodd' : undefined" :clip-path="path.clip ? `url(#${id}-${index})` : undefined" />
		</template>
	</svg>
</template>
