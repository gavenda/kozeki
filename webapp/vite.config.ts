import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import pkg from './package.json' with { type: 'json' };

export default defineConfig({
	plugins: [
		vue(),
		tailwindcss(),
		VitePWA({
			// A new build is taken on the next load; the data is never cached, so nothing waits on it.
			registerType: 'autoUpdate',
			injectRegister: 'auto',
			manifest: {
				name: 'Kozeki',
				short_name: 'Kozeki',
				description: 'Your library, notes and reading statistics.',
				start_url: '/',
				scope: '/',
				display: 'standalone',
				// The same as the light <meta name="theme-color"> in index.html and the page's light background.
				theme_color: '#fcf8ff',
				background_color: '#fcf8ff',
				icons: [
					{ src: '/pwa-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
					{ src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
					{ src: '/pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
				],
			},
			workbox: {
				// The app shell: route chunks, the bundled font and Ui's portraits. The library itself needs the network.
				globPatterns: ['**/*.{js,css,html,png,webp,webmanifest,woff2}'],
				// A navigation is answered with the cached shell — the router takes it from there — but
				// never for the API, which is not a page.
				navigateFallback: '/index.html',
				navigateFallbackDenylist: [/^\/api\//],
				cleanupOutdatedCaches: true,
			},
		}),
	],
	// The footer's version tag reads this rather than duplicating the number
	// in an env var, so it can never drift from what actually shipped.
	define: {
		__APP_VERSION__: JSON.stringify(pkg.version),
	},
	resolve: {
		alias: {
			'@': fileURLToPath(new URL('./src', import.meta.url)),
		},
	},
	server: {
		port: 5173,
		watch: {
			// Wrangler's local D1/KV state lives here and churns on every API call
			// (miniflare's SQLite backing stores get written on each read and write).
			// Left unignored, every one of those writes looks like a source change.
			ignored: ['**/.wrangler/**'],
		},
		proxy: {
			// In production the API is served from the same origin by the Worker.
			// This proxy reproduces that during development, so the
			// frontend always calls a relative `/api` path and never needs to know
			// where the API lives.
			'/api': {
				target: 'http://localhost:8788',
				changeOrigin: true,
			},
		},
	},
});
