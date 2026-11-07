<script setup lang="ts">
/** A titled card around a chart, as the phone's `ChartCard`. */
defineProps<{ title: string }>();
</script>

<template>
	<section class="flex flex-col gap-3 rounded-xl bg-surface-container-low p-4">
		<h3 class="type-title-small text-on-surface-variant">{{ title }}</h3>
		<slot />
	</section>
</template>
