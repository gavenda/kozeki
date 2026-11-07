<script setup lang="ts">
/** A headline number with its label, as the phone's `StatTile`. The value keeps the text colour; it is not a data mark. */
defineProps<{ label: string; value: string }>();
</script>

<template>
	<div class="card min-w-0 flex-1 px-4 py-3">
		<p class="truncate type-label-medium text-on-surface-variant">{{ label }}</p>
		<p class="truncate type-title-large-em">{{ value }}</p>
	</div>
</template>
