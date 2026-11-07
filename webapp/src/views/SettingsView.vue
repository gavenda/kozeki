<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useIntervalFn } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import ButtonGroup from '@/components/ButtonGroup.vue';
import HaloMark from '@/components/HaloMark.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import Portrait from '@/components/Portrait.vue';
import SettingRow from '@/components/SettingRow.vue';
import SettingSection from '@/components/SettingSection.vue';
import SpeechBubble from '@/components/SpeechBubble.vue';
import TopAppBar from '@/components/TopAppBar.vue';
import YearlyGoalDialog from '@/components/YearlyGoalDialog.vue';
import { today } from '@/lib/dates';
import { formatDuration, formatTimeAgo, isUiBirthday } from '@/lib/format';
import { hardcoverAvailable, hardcoverSignedIn, hardcoverSignOut, startHardcoverSignIn } from '@/lib/hardcover';
import * as ops from '@/lib/library';
import { useTheme, type ThemeMode } from '@/lib/theme';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

/**
 * Settings, as the phone's `SettingsScreen` has them, less what only a phone has: comics, backups,
 * the reader and app updates. The goals sync between the two; the theme is this browser's own.
 */
const { user, logout } = useAuth0();
const library = useLibrary();
const route = useRoute();
const { mode, setMode } = useTheme();

// ---- Account and sync ------------------------------------------------------------------------

const signingOut = ref(false);
const origin = window.location.origin;
const now = ref(Date.now());
useIntervalFn(() => (now.value = Date.now()), 30_000);
const syncLine = computed(() => {
	if (library.loading) return s.settings.syncRunning;
	if (library.error) return library.error;
	return library.lastSyncedAt ? s.settings.syncLast(formatTimeAgo(library.lastSyncedAt, now.value)) : s.settings.syncNever;
});

// ---- Hardcover -------------------------------------------------------------------------------

const hardcover = ref(hardcoverSignedIn());
const signingOutOfHardcover = ref(false);
const rejected = computed(() => route.query.hardcover === 'rejected');

// ---- Appearance ------------------------------------------------------------------------------

const themes: {
	value: ThemeMode;
	label: string;
	icon: 'brightnessAutoFill' | 'lightModeFill' | 'darkModeFill';
	color: 'violet' | 'gold' | 'leather';
	iconOnly?: boolean;
}[] = [
	{ value: 'system', label: s.settings.themes.system, icon: 'brightnessAutoFill', color: 'violet', iconOnly: true },
	{ value: 'light', label: s.settings.themes.light, icon: 'lightModeFill', color: 'gold' },
	{ value: 'dark', label: s.settings.themes.dark, icon: 'darkModeFill', color: 'leather' },
];

// ---- Goals -----------------------------------------------------------------------------------

// The daily goal's stops: 5 minutes apart under an hour, 10 under three hours, then an hour apart up to a full day.
const STOPS = [
	...Array.from({ length: 11 }, (_, i) => 5 + 5 * i),
	...Array.from({ length: 12 }, (_, i) => 60 + 10 * i),
	...Array.from({ length: 22 }, (_, i) => 180 + 60 * i),
];
const nearest = (minutes: number) =>
	STOPS.reduce((best, stop, index) => (Math.abs(stop - minutes) < Math.abs(STOPS[best] - minutes) ? index : best), 0);
const dailyGoal = computed(() => library.setting<number>('dailyGoalMinutes', 20));
const dragged = ref(nearest(dailyGoal.value));
watch(dailyGoal, (minutes) => (dragged.value = nearest(minutes)));
const pastDay = ref(false);
function setDailyGoal() {
	library.edit((tx) => ops.setSetting(tx, 'dailyGoalMinutes', STOPS[dragged.value]));
	if (dragged.value === STOPS.length - 1) pastDay.value = true;
}

const year = new Date().getFullYear();
const yearlyGoal = computed(() => {
	const row = library.tables.yearlyGoals.get(String(year));
	return row && ops.alive(row) ? row.books : null;
});
const editingYearlyGoal = ref(false);

const version = __APP_VERSION__;
const birthday = isUiBirthday(today());
</script>

<template>
	<TopAppBar :title="s.settings.title" :back="{ name: 'library' }" always-title />
	<div class="mx-auto flex max-w-[720px] flex-col gap-2 px-4 pb-6">
		<SettingSection :title="s.settings.account">
			<div class="setting-card">
				<SettingRow
					:title="user?.name ?? user?.email ?? ''"
					:subtitle="user?.email !== user?.name ? user?.email : undefined"
					clickable
					@click="signingOut = true"
				>
					<template #leading>
						<img v-if="user?.picture" :src="user.picture" alt="" class="size-9 max-w-none rounded-full" referrerpolicy="no-referrer" />
						<AppIcon v-else name="accountCircleFill" />
					</template>
				</SettingRow>
				<SettingRow
					icon="cloudUpload"
					:title="s.settings.sync"
					:subtitle="syncLine"
					:error="!!library.error && !library.loading"
					clickable
					@click="library.refresh()"
				>
					<template v-if="library.loading" #trailing><HaloMark loading :size="24" /></template>
				</SettingRow>
			</div>
		</SettingSection>

		<SettingSection :title="s.settings.catalogue">
			<p class="setting-label type-label-large">{{ s.settings.catalogueBooks }}</p>
			<div class="setting-card">
				<SettingRow
					icon="personFill"
					:title="s.source.HARDCOVER"
					:subtitle="
						!hardcoverAvailable()
							? s.settings.hardcoverUnavailable
							: hardcover
								? s.settings.hardcoverSignedIn
								: s.settings.hardcoverSignedOut
					"
					:clickable="hardcoverAvailable()"
					@click="hardcover ? (signingOutOfHardcover = true) : startHardcoverSignIn()"
				/>
				<p v-if="rejected" class="ps-15 pe-5 pb-3 type-body-medium text-error">{{ s.settings.hardcoverRejected }}</p>
			</div>
		</SettingSection>

		<SettingSection :title="s.settings.appearance">
			<div class="setting-card">
				<SettingRow icon="paletteFill" :title="s.settings.displayMode" :subtitle="s.settings.displayModeDescription" />
				<ButtonGroup :model-value="mode" :options="themes" class="px-5 pb-3" @update:model-value="setMode" />
			</div>
		</SettingSection>

		<SettingSection :title="s.settings.goals">
			<div class="setting-card">
				<SettingRow icon="timerFill" :title="s.settings.dailyGoal" :subtitle="formatDuration(STOPS[dragged] * 60_000)" />
				<div class="px-5 pb-3">
					<input
						v-model.number="dragged"
						type="range"
						min="0"
						:max="STOPS.length - 1"
						step="1"
						class="w-full accent-primary"
						:aria-label="s.settings.dailyGoal"
						:aria-valuetext="formatDuration(STOPS[dragged] * 60_000)"
						@change="setDailyGoal"
					/>
				</div>
				<SettingRow
					icon="flagFill"
					:title="s.settings.yearlyGoal(year)"
					:subtitle="yearlyGoal != null ? String(yearlyGoal) : s.settings.goalNotSet"
					clickable
					@click="editingYearlyGoal = true"
				/>
			</div>
		</SettingSection>

		<div class="flex flex-col items-center gap-2 px-2 py-4 text-center">
			<div class="flex items-end gap-1">
				<Portrait face="content" :width="96" />
				<SpeechBubble class="flex min-h-24 max-w-[360px] items-center text-start">{{
					birthday ? s.settings.birthday : s.settings.credits
				}}</SpeechBubble>
			</div>
			<p class="type-title-medium">{{ s.settings.version(version) }}</p>
			<p class="type-body-small text-on-surface-variant">
				{{ s.settings.keeperBefore }}<span class="text-primary">{{ s.characterName }}</span
				>{{ s.settings.keeperAfter }}
			</p>
			<p class="type-label-small text-outline">{{ s.settings.illustrator }}</p>
		</div>
	</div>

	<ModalDialog v-if="signingOut" :title="s.settings.accountSignOutTitle" icon="logout" icon-class="text-error" @close="signingOut = false">
		<p>{{ s.settings.accountSignOutMessage }}</p>
		<template #actions="{ close }">
			<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-filled" @click="logout({ logoutParams: { returnTo: `${origin}/` } })">{{ s.settings.signOut }}</button>
		</template>
	</ModalDialog>
	<ModalDialog
		v-if="signingOutOfHardcover"
		:title="s.settings.hardcoverSignOutTitle"
		icon="logout"
		icon-class="text-error"
		@close="signingOutOfHardcover = false"
	>
		<p>{{ s.settings.hardcoverSignOutMessage }}</p>
		<template #actions="{ close }">
			<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-filled" @click="(hardcoverSignOut(), (hardcover = false), close())">{{ s.settings.signOut }}</button>
		</template>
	</ModalDialog>
	<ModalDialog v-if="pastDay" :title="s.settings.pastDayTitle" @close="pastDay = false">
		<template #icon><Portrait face="dumbfounded" :width="96" /></template>
		<p class="text-center">{{ s.settings.pastDayMessage }}</p>
		<template #actions="{ close }"
			><button class="btn-text" @click="close">{{ s.action.close }}</button></template
		>
	</ModalDialog>
	<YearlyGoalDialog
		v-if="editingYearlyGoal"
		:year="year"
		:books="yearlyGoal"
		@save="(books) => (library.edit((tx) => ops.setYearlyGoal(tx, year, books)), (editingYearlyGoal = false))"
		@close="editingYearlyGoal = false"
	/>
</template>
