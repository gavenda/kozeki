<script setup lang="ts">
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/** The way to Settings from a top-level page: a leather gear on a tonal button, as the phone's `SettingsButton`. */
</script>

<template>
	<RouterLink
		:to="{ name: 'settings' }"
		class="icon-btn-tonal h-10 w-12 bg-surface-container-high text-tertiary"
		:aria-label="s.settings.title"
		:title="s.settings.title"
	>
		<AppIcon name="settingsFill" />
	</RouterLink>
</template>
