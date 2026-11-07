<script setup lang="ts">
import { formatPercent } from '@/lib/format';
import WavyRing from './WavyRing.vue';

/** How far into a book its reader is: a small wavy ring with the percentage in it, as the phone's `ReadingProgressRing`. */
defineProps<{ progress: number }>();
</script>

<template>
	<WavyRing :progress="progress" :size="32" :stroke="2" :gap="3" :wavelength="10" :amplitude="1" tone="primary">
		<span class="text-[9px] leading-[10px] font-medium">{{ formatPercent(progress).replace('%', '') }}</span>
	</WavyRing>
</template>
