import { computed, ref, watchEffect } from 'vue';

const STORAGE_KEY = 'kozeki.theme';

export type ThemeMode = 'system' | 'light' | 'dark';

function stored(): ThemeMode {
	try {
		const value = localStorage.getItem(STORAGE_KEY);
		if (value === 'light' || value === 'dark') return value;
	} catch {
		// Following the system, then.
	}
	return 'system';
}

const systemQuery = window.matchMedia?.('(prefers-color-scheme: dark)');
const mode = ref<ThemeMode>(stored());
const systemDark = ref(systemQuery?.matches ?? false);
systemQuery?.addEventListener?.('change', (event) => {
	systemDark.value = event.matches;
});

const theme = computed(() => (mode.value === 'system' ? (systemDark.value ? 'dark' : 'light') : mode.value));

watchEffect(() => {
	document.documentElement.classList.toggle('dark', theme.value === 'dark');
});

function setMode(next: ThemeMode): void {
	mode.value = next;
	try {
		if (next === 'system') localStorage.removeItem(STORAGE_KEY);
		else localStorage.setItem(STORAGE_KEY, next);
	} catch {
		// A remembered theme is a convenience.
	}
}

/** Light or dark, per browser, as the Android app keeps it per device. */
export function useTheme() {
	return { mode, theme, setMode };
}
