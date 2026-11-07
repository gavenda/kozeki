<script setup lang="ts">
import AppIcon from './AppIcon.vue';

const props = defineProps<{ modelValue: number | null | undefined; readonly?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>();

function iconFor(star: number) {
	const value = props.modelValue ?? 0;
	if (value >= star) return 'starFill' as const;
	if (value >= star - 0.5) return 'starHalf' as const;
	return 'star' as const;
}

/** The left half of a star gives half a star. Choosing the rating already set clears it. */
function pick(star: number, event: MouseEvent) {
	if (props.readonly) return;
	const target = event.currentTarget as HTMLElement;
	const half = event.offsetX < target.clientWidth / 2;
	const value = half ? star - 0.5 : star;
	emit('update:modelValue', value === props.modelValue ? null : value);
}
</script>

<template>
	<div class="flex text-secondary dark:text-secondary">
		<button
			v-for="star in 5"
			:key="star"
			type="button"
			:disabled="readonly"
			class="cursor-pointer p-0.5 disabled:cursor-default"
			:aria-label="`${star} stars`"
			@click="pick(star, $event)"
		>
			<AppIcon :name="iconFor(star)" :size="28" />
		</button>
	</div>
</template>
