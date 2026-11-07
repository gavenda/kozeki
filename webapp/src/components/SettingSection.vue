<script setup lang="ts">
/**
 * A titled group of Settings, as the phone's `Section` and `SplitSection`. Its cards are the
 * children given `class="setting-card"`: one is a 28px round card, several join like a connected
 * button group, 2px apart, round on the outside and nearly square between.
 */
defineProps<{ title: string }>();
</script>

<template>
	<section class="flex flex-col gap-3 py-2">
		<h2 class="px-2 type-title-medium text-primary">{{ title }}</h2>
		<div class="setting-cards flex flex-col gap-0.5">
			<slot />
		</div>
	</section>
</template>

<style scoped>
.setting-cards > :deep(.setting-card) {
	border-radius: 4px;
	background: var(--color-surface-container);
	padding-block: 8px;
	overflow: hidden;
}
.setting-cards > :deep(.setting-card:first-child) {
	border-top-left-radius: 28px;
	border-top-right-radius: 28px;
}
.setting-cards > :deep(.setting-card:last-child) {
	border-bottom-left-radius: 28px;
	border-bottom-right-radius: 28px;
}
.setting-cards > :deep(.setting-label) {
	border-radius: 4px;
	background: var(--color-surface-container-highest);
	color: var(--color-on-surface-variant);
	padding: 14px 20px;
}
.setting-cards > :deep(.setting-label:first-child) {
	border-top-left-radius: 28px;
	border-top-right-radius: 28px;
}
</style>
