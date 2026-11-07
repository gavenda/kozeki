<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useEventListener } from '@vueuse/core';
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import type { IconName } from '@/lib/icons';
import '@/lib/theme';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

const { isAuthenticated } = useAuth0();
const route = useRoute();
const router = useRouter();
const library = useLibrary();

const destinations: { name: string; label: string; icon: IconName; active: IconName }[] = [
	{ name: 'library', label: s.nav.library, icon: 'shelves', active: 'shelvesFill' },
	{ name: 'calendar', label: s.nav.calendar, icon: 'calendarMonth', active: 'calendarMonthFill' },
	{ name: 'statistics', label: s.nav.statistics, icon: 'barChart', active: 'barChartFill' },
	{ name: 'settings', label: s.nav.settings, icon: 'settings', active: 'settingsFill' },
];

const chrome = computed(() => isAuthenticated.value && !route.meta.public && route.name !== 'hardcover');
const current = computed(() => {
	if (route.name === 'book' || route.name === 'add') return 'library';
	return route.name as string;
});

// The library loads once signed in, and catches up whenever the tab comes back into view.
watch(
	isAuthenticated,
	(signedIn) => {
		if (signedIn && !library.loaded) void library.refresh();
		if (!signedIn && route.meta.public !== true && route.name) void router.replace({ name: 'login' });
	},
	{ immediate: true },
);
useEventListener(document, 'visibilitychange', () => {
	if (document.visibilityState === 'visible' && library.loaded) void library.refresh();
});
</script>

<template>
	<div v-if="!chrome">
		<RouterView />
	</div>
	<div v-else class="min-h-dvh md:pl-24">
		<!-- A rail on wide screens, a bar along the bottom on a phone. -->
		<nav
			class="fixed inset-x-0 bottom-0 z-20 flex justify-around border-t border-outline-variant/40 bg-surface-container pb-[env(safe-area-inset-bottom)] md:inset-y-0 md:right-auto md:w-24 md:flex-col md:justify-start md:gap-3 md:border-t-0 md:pt-6"
		>
			<RouterLink
				v-for="destination in destinations"
				:key="destination.name"
				:to="{ name: destination.name }"
				class="group flex flex-1 flex-col items-center gap-1 py-3 text-xs font-medium text-on-surface-variant md:flex-none md:py-1"
			>
				<span
					class="flex h-8 w-16 items-center justify-center rounded-full transition-colors"
					:class="current === destination.name ? 'bg-secondary-container text-on-secondary-container' : 'group-hover:bg-on-surface/10'"
				>
					<AppIcon :name="current === destination.name ? destination.active : destination.icon" />
				</span>
				<span :class="current === destination.name ? 'text-on-surface' : ''">{{ destination.label }}</span>
			</RouterLink>
		</nav>

		<main class="mx-auto max-w-6xl px-4 pt-4 pb-28 md:px-8 md:pb-10">
			<div
				v-if="library.loaded && library.error"
				class="mb-4 rounded-2xl bg-error-container px-4 py-3 text-sm text-on-error-container"
				role="alert"
			>
				{{ s.saveFailed(library.error) }}
			</div>
			<EmptyState v-if="!library.loaded && library.loading" face="studious" :title="s.loading" />
			<EmptyState v-else-if="!library.loaded" face="dismayed" :title="s.loadFailed.title" :message="library.error ?? undefined">
				<button class="btn-filled" @click="library.refresh()">{{ s.loadFailed.action }}</button>
			</EmptyState>
			<RouterView v-else />
		</main>
	</div>
</template>
