<script setup lang="ts">
import type { IconName } from '@/lib/icons';
import AppIcon from './AppIcon.vue';

/**
 * A row of Settings, as the phone's `SettingRow`: a 24px icon, the title and a line under it, and
 * anything trailing. A clickable one is a button, with no chevron.
 */
defineProps<{ icon?: IconName; title: string; subtitle?: string; error?: boolean; clickable?: boolean }>();
defineEmits<{ click: [] }>();
</script>

<template>
	<component
		:is="clickable ? 'button' : 'div'"
		:type="clickable ? 'button' : undefined"
		class="flex w-full items-center gap-4 px-5 py-3 text-start"
		:class="{ 'cursor-pointer transition-colors hover:bg-on-surface/8': clickable }"
		@click="clickable && $emit('click')"
	>
		<span class="flex size-6 shrink-0 items-center justify-center text-on-surface-variant">
			<slot name="leading"><AppIcon v-if="icon" :name="icon" /></slot>
		</span>
		<span class="min-w-0 flex-1">
			<span class="block type-title-medium">{{ title }}</span>
			<span v-if="subtitle" class="block type-body-medium" :class="error ? 'text-error' : 'text-on-surface-variant'">{{ subtitle }}</span>
		</span>
		<slot name="trailing" />
	</component>
</template>
