<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { s } from '@/strings';
import SortDetailLine, { type SortDetail } from './SortDetailLine.vue';

/**
 * The header a series or a shelf stays under while its books scroll by, as the phone's
 * `GroupHeader`: its name in leather, the count or what the sort sums up, and a button that folds
 * the books away. Clicking anywhere on it folds too. The `actions` slot is a shelf's menu.
 */
defineProps<{ title: string; count: number; summary?: SortDetail | null; collapsed: boolean }>();
const emit = defineEmits<{ toggle: [] }>();
</script>

<template>
	<div
		class="sticky top-[var(--library-header)] z-10 flex h-12 cursor-pointer items-center gap-2 border-b border-on-surface/10 bg-surface ps-2"
		@click="emit('toggle')"
	>
		<p class="min-w-0 truncate type-title-medium text-tertiary">{{ title }}</p>
		<SortDetailLine v-if="summary" :detail="summary" :star-size="14" text-class="shrink-0 type-body-small text-on-surface-variant" />
		<span v-else class="shrink-0 type-body-small text-on-surface-variant">{{ count }}</span>
		<span class="flex-1" />
		<slot name="actions" />
		<button
			class="icon-btn-tonal h-9 w-10 bg-tertiary-container text-on-tertiary-container"
			:aria-label="collapsed ? s.library.groupExpand : s.library.groupCollapse"
			:aria-expanded="!collapsed"
			@click.stop="emit('toggle')"
		>
			<AppIcon name="keyboardArrowUp" class="transition-transform" :class="{ 'rotate-180': collapsed }" />
		</button>
	</div>
</template>
