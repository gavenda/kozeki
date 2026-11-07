<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useEventListener } from '@vueuse/core';
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import HaloMark from '@/components/HaloMark.vue';
import type { IconName } from '@/lib/icons';
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
	<div v-else class="min-h-dvh" :class="tab ? 'pb-20 md:ps-24 md:pb-0' : ''">
		<!-- A rail on wide windows, a bar along the bottom on a phone, as NavigationSuiteScaffold. -->
		<nav
			v-if="tab"
			class="fixed inset-x-0 bottom-0 z-40 flex justify-around bg-surface-container pb-[env(safe-area-inset-bottom)] md:inset-y-0 md:end-auto md:w-24 md:flex-col md:justify-start md:gap-3 md:bg-surface md:pt-11"
		>
			<RouterLink
				v-for="destination in destinations"
				:key="destination.name"
				:to="{ name: destination.name }"
				class="group flex flex-1 flex-col items-center gap-1 pt-3 pb-4 type-label-medium text-on-surface-variant md:flex-none md:py-1"
				:aria-current="tab === destination.name ? 'page' : undefined"
			>
				<span
					class="flex h-8 w-14 items-center justify-center rounded-full transition-colors"
					:class="tab === destination.name ? 'bg-secondary-container text-on-secondary-container' : 'group-hover:bg-on-surface/10'"
				>
					<AppIcon :name="tab === destination.name ? destination.active : destination.icon" />
				</span>
				<span :class="tab === destination.name ? 'text-secondary' : ''">{{ destination.label }}</span>
			</RouterLink>
		</nav>

		<main>
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
				<button class="btn-filled" @click="library.refresh()">{{ s.loadFailed.action }}</button>
			</EmptyState>
			<RouterView v-else />
		</main>
	</div>
</template>
