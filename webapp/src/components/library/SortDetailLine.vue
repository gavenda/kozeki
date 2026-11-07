<script setup lang="ts">
import RatingBar from '@/components/RatingBar.vue';

export type SortDetail = { kind: 'line'; text: string } | { kind: 'stars'; rating: number; label?: string };

/** What a book shows in place of its authors under some sorts: when it was read, what it cost, its stars. */
defineProps<{ detail: SortDetail; starSize: number; textClass: string }>();
</script>

<template>
	<p v-if="detail.kind === 'line'" class="truncate" :class="textClass">{{ detail.text }}</p>
	<div v-else class="flex items-center gap-1" :class="textClass">
		<RatingBar :rating="detail.rating" :size="starSize" />
		<span v-if="detail.label">{{ detail.label }}</span>
	</div>
</template>
