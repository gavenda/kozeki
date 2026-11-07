<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useEventListener, useMediaQuery } from '@vueuse/core';
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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

// Collapsed, the rail is 6rem wide with the labels under the icons; expanded, it is 15rem with them beside.
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
	<div
		v-else
		class="min-h-dvh md:transition-[padding] md:duration-300 md:ease-out motion-reduce:transition-none"
		:class="tab ? (expanded ? 'pb-20 md:ps-60 md:pb-0' : 'pb-20 md:ps-24 md:pb-0') : ''"
	>
		<!-- A rail on wide windows, a bar along the bottom on a phone, as NavigationSuiteScaffold. -->
		<nav
			v-if="tab"
			:class="expanded ? 'md:w-60' : 'md:w-24'"
			class="fixed inset-x-0 bottom-0 z-40 flex md:transition-[width] md:duration-300 md:ease-out motion-reduce:transition-none justify-around bg-surface-container pb-[env(safe-area-inset-bottom)] md:inset-y-0 md:end-auto md:flex-col md:justify-start md:bg-surface md:pt-11"
		>
			<button
				v-if="wide"
				class="mb-4 ms-6 flex size-12 items-center justify-center rounded-full text-on-surface-variant transition-[margin,background-color] duration-300 hover:bg-on-surface/10 motion-reduce:transition-none"
				:aria-label="expanded ? s.nav.collapse : s.nav.expand"
				:aria-expanded="expanded"
				@click="expanded = !expanded"
			>
				<AppIcon :name="expanded ? 'menuOpen' : 'menu'" />
			</button>
			<!-- The showing page's main action, at the top of the rail as the phone's wide window has it. -->
			<div id="rail-action" class="contents"></div>
			<!-- One layout for both widths, so the rail can animate between them: the label sits under the icon
				while collapsed and inside the pill while expanded, and the pill grows to hold it. -->
			<RouterLink
				v-for="destination in destinations"
				:key="destination.name"
				:to="{ name: destination.name }"
				class="group relative flex flex-1 flex-col items-center gap-1 pt-3 pb-4 type-label-medium text-on-surface-variant transition-[margin] duration-300 md:ms-5 md:h-14 md:flex-none md:items-start md:self-start md:gap-0 md:p-0 motion-reduce:transition-none"
				:class="expanded ? 'md:mb-0' : 'md:mb-3'"
				:aria-current="tab === destination.name ? 'page' : undefined"
			>
				<span
					class="flex h-8 w-14 items-center justify-center rounded-full transition-[height,margin,padding,background-color] duration-300 ease-out md:w-auto md:justify-start md:px-4 motion-reduce:transition-none"
					:class="[
						tab === destination.name ? 'bg-secondary-container text-on-secondary-container' : 'group-hover:bg-on-surface/10',
						expanded ? 'md:mt-0 md:h-14 md:ps-5 md:pe-4' : 'md:mt-1',
					]"
				>
					<AppIcon :name="tab === destination.name ? destination.active : destination.icon" />
					<span
						class="hidden transition-[grid-template-columns,opacity] duration-300 ease-out md:grid motion-reduce:transition-none"
						:class="expanded ? 'grid-cols-[1fr] opacity-100' : 'grid-cols-[0fr] opacity-0'"
						aria-hidden="true"
					>
						<span class="overflow-hidden ps-3 type-label-large whitespace-nowrap">{{ destination.label }}</span>
					</span>
				</span>
				<span
					class="transition-opacity duration-200 md:pointer-events-none md:absolute md:top-10 md:left-1/2 md:-translate-x-1/2 md:whitespace-nowrap motion-reduce:transition-none"
					:class="[tab === destination.name ? 'text-secondary' : '', expanded ? 'md:opacity-0' : '']"
					>{{ destination.label }}</span
				>
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
