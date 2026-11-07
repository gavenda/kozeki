<script setup lang="ts">
import { icons, type IconName } from '@/lib/icons';

defineProps<{ name: IconName; size?: number }>();
</script>

<template>
	<svg :width="size ?? 24" :height="size ?? 24" viewBox="0 -960 960 960" fill="currentColor" aria-hidden="true" class="shrink-0">
		<path :d="icons[name]" />
	</svg>
</template>
