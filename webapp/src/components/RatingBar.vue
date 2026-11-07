<script setup lang="ts">
import { computed } from 'vue';
import type { IconName } from '@/lib/icons';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/**
 * Five stars in half-star steps, as the phone's `RatingBar`. Read-only unless it is listened to.
 * Tapping a star sets it; tapping the star that is already the rating drops it by half, which is
 * how half stars are reached without tiny targets.
 */
const props = withDefaults(defineProps<{ rating?: number | null; size?: number; editable?: boolean }>(), { size: 32 });
const emit = defineEmits<{ change: [value: number] }>();
const value = computed(() => props.rating ?? 0);

function icon(star: number): IconName {
	if (value.value >= star) return 'starFill';
	if (value.value >= star - 0.5) return 'starHalf';
	return 'star';
}
const tint = (star: number) => (value.value >= star - 0.5 ? 'text-primary' : 'text-outline-variant');
const description = computed(() => (props.rating == null ? s.rating.none : s.rating.value(props.rating)));
</script>

<template>
	<div v-if="editable" class="flex gap-1" role="group" :aria-label="description">
		<button
			v-for="star in 5"
			:key="star"
			type="button"
			class="cursor-pointer rounded-full transition-colors hover:bg-primary/10"
			:class="tint(star)"
			:aria-label="s.rating.stars(star)"
			@click="emit('change', value === star ? star - 0.5 : star)"
		>
			<AppIcon :name="icon(star)" :size="size" />
		</button>
	</div>
	<div v-else class="flex" role="img" :aria-label="description">
		<AppIcon v-for="star in 5" :key="star" :name="icon(star)" :size="size" :class="tint(star)" />
	</div>
</template>
