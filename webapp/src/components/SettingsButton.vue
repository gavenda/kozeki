<script setup lang="ts">
import { IconButton } from 'tailwind-material3';
import { useRouter } from 'vue-router';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/** The way to Settings from a top-level page: a leather gear on a tonal button, as the phone's `SettingsButton`. */
const router = useRouter();
</script>

<template>
	<IconButton
		variant="tonal"
		width="wide"
		:label="s.settings.title"
		:title="s.settings.title"
		class="bg-surface-container-high! text-tertiary!"
		@click="router.push({ name: 'settings' })"
	>
		<AppIcon name="settingsFill" />
	</IconButton>
</template>
