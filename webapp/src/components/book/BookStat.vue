<script setup lang="ts">
/** A figure on a progress card with its label over it, as the phone's `Stat`. */
defineProps<{ label: string; value: string }>();
</script>

<template>
	<div>
		<p class="type-label-medium text-on-surface-variant">{{ label }}</p>
		<p class="type-title-medium-em">{{ value }}</p>
	</div>
</template>
