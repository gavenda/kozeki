<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { optionIconButtonClasses, type OptionColor } from '@/lib/optionColor';
import { s } from '@/strings';
import { IconButton } from 'tailwind-material3';

/** Steps through the days, weeks, months or years, as the phone's `PeriodNavigator`, in the range's own colour. */
defineProps<{ label: string; color: OptionColor; canGoForward: boolean }>();
const emit = defineEmits<{ previous: []; next: [] }>();
</script>

<template>
	<div class="flex items-center">
		<IconButton
			variant="tonal"
			:label="s.stats.previous"
			:class="optionIconButtonClasses(color)"
			:title="s.stats.previous"
			@click="emit('previous')"
		>
			<AppIcon name="keyboardArrowLeft" />
		</IconButton>
		<p class="flex-1 text-center type-title-medium-em">{{ label }}</p>
		<IconButton
			variant="tonal"
			:label="s.stats.next"
			:class="optionIconButtonClasses(color)"
			:disabled="!canGoForward"
			:title="s.stats.next"
			@click="emit('next')"
		>
			<AppIcon name="keyboardArrowRight" />
		</IconButton>
	</div>
</template>
