import { createPinia } from 'pinia';
import { createApp, h } from 'vue';
import { auth0, isConfigured } from '@/lib/auth0';
import { shouldReloadForStaleChunk } from '@/lib/staleChunk';
import { router } from '@/router';
import { s } from '@/strings';
import App from './App.vue';
import '@fontsource-variable/google-sans-flex';
import './style.css';

// A route chunk missing after a deploy means a stale tab: reload for the new build.
window.addEventListener('vite:preloadError', (event) => {
	if (!shouldReloadForStaleChunk()) return;
	event.preventDefault();
	window.location.reload();
});

if (!isConfigured) {
	createApp({ render: () => h('p', { class: 'p-8' }, s.notConfigured) }).mount('#app');
} else {
	// Router before Auth0: the plugin navigates with it after a sign-in.
	createApp(App).use(createPinia()).use(router).use(auth0).mount('#app');
}
