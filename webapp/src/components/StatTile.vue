<script setup lang="ts">
import { Card } from 'tailwind-material3';
/** A headline number with its label, as the phone's `StatTile`. The value keeps the text colour; it is not a data mark. */
defineProps<{ label: string; value: string }>();
</script>

<template>
	<Card :padded="false" class="min-w-0 flex-1 bg-surface-container! px-4 py-3">
		<p class="truncate type-label-medium text-on-surface-variant">{{ label }}</p>
		<p class="truncate type-title-large-em">{{ value }}</p>
	</Card>
</template>
