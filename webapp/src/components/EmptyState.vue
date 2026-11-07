<script setup lang="ts">
import type { IconName } from '@/lib/icons';
import AppIcon from './AppIcon.vue';
import Portrait, { type Face } from './Portrait.vue';

/**
 * Shown where a list would be, with what to do about it being empty, as the phone's `EmptyState`.
 * [face] is the one Ui makes at it, [icon] what it is about, on a badge at her shoulder.
 */
defineProps<{ face: Face; icon?: IconName; title: string; message?: string }>();
</script>

<template>
	<div class="flex w-full flex-col items-center gap-3 px-8 py-12 text-center">
		<div class="relative">
			<Portrait :face="face" />
			<span
				v-if="icon"
				class="absolute end-0 bottom-0 flex size-10 items-center justify-center rounded-full bg-secondary-container text-on-secondary-container"
			>
				<AppIcon :name="icon" />
			</span>
		</div>
		<h2 class="type-title-large-em">{{ title }}</h2>
		<p v-if="message" class="max-w-[360px] type-body-medium text-on-surface-variant">{{ message }}</p>
		<slot />
	</div>
</template>
