<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useEventListener, useMediaQuery } from '@vueuse/core';
import { computed, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { Button, NavigationBar, NavigationItem, NavigationRail, NavigationRailMenuButton } from 'tailwind-material3';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import type { IconName } from '@/lib/icons';
import { railExpanded as expanded } from '@/lib/rail';
import '@/lib/theme';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

const { isAuthenticated } = useAuth0();
const route = useRoute();
const router = useRouter();
const library = useLibrary();

// The phone's three top-level destinations. Settings is reached from each one's top bar.
const destinations: { name: 'library' | 'calendar' | 'statistics'; label: string; icon: IconName; active: IconName }[] = [
	{ name: 'library', label: s.nav.library, icon: 'shelves', active: 'shelvesFill' },
	{ name: 'calendar', label: s.nav.calendar, icon: 'calendarMonth', active: 'calendarMonthFill' },
	{ name: 'statistics', label: s.nav.statistics, icon: 'leaderboard', active: 'leaderboardFill' },
];

// A rail on wide windows, collapsed with the labels under the icons or expanded with them beside.
const wide = useMediaQuery('(min-width: 48rem)');
const chrome = computed(() => isAuthenticated.value && !route.meta.public && route.name !== 'hardcover');
// The rail is there for the top-level pages only; one reached from them gets the whole window.
const tab = computed(() => route.meta.tab);

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
	<div v-else class="min-h-dvh" :class="tab ? 'pb-20 md:flex md:pb-0' : ''">
		<!-- A rail on wide windows, a bar along the bottom on a phone, as NavigationSuiteScaffold. -->
		<NavigationRail v-if="tab && wide" v-model:expanded="expanded" :model-value="tab" class="sticky! top-0 h-dvh! self-start">
			<template #header>
				<NavigationRailMenuButton :expand-label="s.nav.expand" :collapse-label="s.nav.collapse" />
				<!-- The showing page's main action, as the phone's wide window has it. -->
				<div id="rail-action" class="mt-1"></div>
			</template>
			<NavigationItem
				v-for="destination in destinations"
				:key="destination.name"
				:as="RouterLink"
				:to="{ name: destination.name }"
				:value="destination.name"
				:label="destination.label"
			>
				<template #icon><AppIcon :name="destination.icon" /></template>
				<template #active-icon><AppIcon :name="destination.active" /></template>
			</NavigationItem>
		</NavigationRail>
		<NavigationBar v-else-if="tab" :model-value="tab" class="fixed inset-x-0 bottom-0 z-40 pb-[env(safe-area-inset-bottom)]">
			<NavigationItem
				v-for="destination in destinations"
				:key="destination.name"
				:as="RouterLink"
				:to="{ name: destination.name }"
				:value="destination.name"
				:label="destination.label"
			>
				<template #icon><AppIcon :name="destination.icon" /></template>
				<template #active-icon><AppIcon :name="destination.active" /></template>
			</NavigationItem>
		</NavigationBar>

		<main class="min-w-0 flex-1">
			<div
				v-if="library.loaded && library.error"
				class="sticky top-0 z-50 mx-auto max-w-[720px] rounded-b-xl bg-error-container px-4 py-3 type-body-medium text-on-error-container"
				role="alert"
			>
				{{ s.saveFailed(library.error) }}
			</div>
			<EmptyState v-if="!library.loaded && library.loading" face="studious" :title="s.loading">
				<HaloMark loading :aria-label="s.loading" />
			</EmptyState>
			<EmptyState v-else-if="!library.loaded" face="dismayed" :title="s.loadFailed.title" :message="library.error ?? undefined">
				<Button @click="library.refresh()">{{ s.loadFailed.action }}</Button>
			</EmptyState>
			<RouterView v-else />
		</main>
	</div>
</template>
