<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { optionIconButtonClasses, type OptionColor } from '@/lib/optionColor';
import { s } from '@/strings';

/** Steps through the days, weeks, months or years, as the phone's `PeriodNavigator`, in the range's own colour. */
defineProps<{ label: string; color: OptionColor; canGoForward: boolean }>();
const emit = defineEmits<{ previous: []; next: [] }>();
</script>

<template>
	<div class="flex items-center">
		<button
			class="icon-btn-tonal"
			:class="optionIconButtonClasses(color)"
			:aria-label="s.stats.previous"
			:title="s.stats.previous"
			@click="emit('previous')"
		>
			<AppIcon name="keyboardArrowLeft" />
		</button>
		<p class="flex-1 text-center type-title-medium-em">{{ label }}</p>
		<button
			class="icon-btn-tonal"
			:class="optionIconButtonClasses(color)"
			:disabled="!canGoForward"
			:aria-label="s.stats.next"
			:title="s.stats.next"
			@click="emit('next')"
		>
			<AppIcon name="keyboardArrowRight" />
		</button>
	</div>
</template>
