import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';

/** The frontend suite: the library's write rules and the statistics. */
export default defineConfig({
	// The router lazy-imports its views, so resolving them needs the Vue plugin
	// even though no component is mounted here.
	plugins: [vue()],
	resolve: {
		alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
	},
	test: {
		include: ['src/**/*.spec.ts'],
		// The router and the Auth0 plugin both reach for `window`, so the frontend
		// suite runs against a DOM rather than bare Node.
		environment: 'happy-dom',
	},
});
