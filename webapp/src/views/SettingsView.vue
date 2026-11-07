<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { hardcoverAvailable, hardcoverSignedIn, hardcoverSignOut, startHardcoverSignIn } from '@/lib/hardcover';
import * as ops from '@/lib/library';
import { useTheme, type ThemeMode } from '@/lib/theme';
import { useLibrary } from '@/stores/library';
import { s } from '@/strings';

const { user, logout } = useAuth0();
const library = useLibrary();
const route = useRoute();
const { mode, setMode } = useTheme();

const year = new Date().getFullYear();
const dailyGoal = ref(library.setting<number>('dailyGoalMinutes', 20));
watch(
	() => library.setting<number>('dailyGoalMinutes', 20),
	(value) => (dailyGoal.value = value),
);

const yearlyGoal = computed(() => {
	const row = library.tables.yearlyGoals.get(String(year));
	return row && ops.alive(row) ? row.books : null;
});
const yearlyDraft = ref<string>(yearlyGoal.value?.toString() ?? '');

function saveDailyGoal() {
	const minutes = Math.min(24 * 60, Math.max(1, Math.round(dailyGoal.value)));
	library.edit((tx) => ops.setSetting(tx, 'dailyGoalMinutes', minutes));
}
function saveYearlyGoal() {
	const books = Number(yearlyDraft.value);
	if (books > 0) library.edit((tx) => ops.setYearlyGoal(tx, year, books));
}

const hardcover = ref(hardcoverSignedIn());
const rejected = computed(() => route.query.hardcover === 'rejected');
function signOutOfHardcover() {
	hardcoverSignOut();
	hardcover.value = false;
}

const themes: ThemeMode[] = ['system', 'light', 'dark'];
const version = __APP_VERSION__;
const origin = window.location.origin;
</script>

<template>
	<div class="flex max-w-2xl flex-col gap-8">
		<h1 class="text-2xl font-medium">{{ s.settings.title }}</h1>

		<section class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.settings.account }}</h2>
			<div class="card flex items-center gap-4 p-5">
				<img v-if="user?.picture" :src="user.picture" alt="" class="size-10 rounded-full" referrerpolicy="no-referrer" />
				<div class="min-w-0 flex-1">
					<p class="truncate font-medium">{{ user?.name ?? user?.email }}</p>
					<p v-if="user?.email && user.email !== user.name" class="truncate text-sm text-on-surface-variant">{{ user.email }}</p>
				</div>
				<button class="btn-text" @click="logout({ logoutParams: { returnTo: `${origin}/` } })">{{ s.settings.signOut }}</button>
			</div>
		</section>

		<section class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.settings.goals }}</h2>
			<div class="card flex flex-col gap-4 p-5">
				<label class="flex flex-col gap-2">
					<span class="flex justify-between text-sm"
						><span>{{ s.settings.dailyGoal }}</span
						><span class="text-on-surface-variant">{{ s.settings.minutes(dailyGoal) }}</span></span
					>
					<input v-model.number="dailyGoal" type="range" min="5" max="240" step="5" class="accent-primary" @change="saveDailyGoal" />
				</label>
				<form class="flex items-end gap-2" @submit.prevent="saveYearlyGoal">
					<label class="flex-1"
						><span class="label">{{ s.settings.yearlyGoal(year) }}</span
						><input v-model="yearlyDraft" class="field" inputmode="numeric" pattern="\d*"
					/></label>
					<button class="btn-tonal" :disabled="!(Number(yearlyDraft) > 0) || Number(yearlyDraft) === yearlyGoal">{{ s.book.save }}</button>
				</form>
			</div>
		</section>

		<section class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.settings.theme }}</h2>
			<div class="flex gap-2">
				<button v-for="theme in themes" :key="theme" class="chip" :class="{ 'chip-on': mode === theme }" @click="setMode(theme)">
					{{ s.settings.themes[theme] }}
				</button>
			</div>
		</section>

		<section class="flex flex-col gap-3">
			<h2 class="section-title">{{ s.settings.catalogue }}</h2>
			<div class="card flex items-center gap-4 p-5">
				<div class="flex-1">
					<p class="font-medium">{{ s.settings.hardcover }}</p>
					<p class="text-sm text-on-surface-variant">
						{{
							!hardcoverAvailable()
								? s.settings.hardcoverUnavailable
								: hardcover
									? s.settings.hardcoverSignedIn
									: s.settings.hardcoverSignedOut
						}}
					</p>
					<p v-if="rejected" class="text-sm text-error">{{ s.settings.hardcoverRejected }}</p>
				</div>
				<template v-if="hardcoverAvailable()">
					<button v-if="hardcover" class="btn-text" @click="signOutOfHardcover">{{ s.settings.signOut }}</button>
					<button v-else class="btn-tonal" @click="startHardcoverSignIn">{{ s.settings.hardcoverSignIn }}</button>
				</template>
			</div>
		</section>

		<p class="px-1 text-xs text-on-surface-variant">{{ s.settings.version(version) }}</p>
	</div>
</template>
