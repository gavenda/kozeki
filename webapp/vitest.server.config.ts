import { cloudflareTest, readD1Migrations } from '@cloudflare/vitest-plugin';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const migrations = await readD1Migrations(fileURLToPath(new URL('./migrations', import.meta.url)));

/**
 * The API runs inside workerd for real, against a migrated D1 database.
 *
 * The bindings are declared here rather than read from `wrangler.jsonc`, so the
 * tests get a stand-in Auth0 tenant and need no built `dist/`. `main` is the
 * production Worker entry, so routing is exercised as it ships.
 */
export default defineConfig({
	plugins: [
		cloudflareTest({
			main: './server/index.ts',
			miniflare: {
				compatibilityDate: '2026-09-11',
				compatibilityFlags: ['nodejs_compat'],
				d1Databases: { DB: 'kozeki-test' },
				kvNamespaces: ['CACHE'],
				r2Buckets: ['COVERS'],
				bindings: {
					// Handed to `applyD1Migrations` in the setup file.
					TEST_MIGRATIONS: migrations,
					// A stand-in Auth0 tenant. Tests publish a JWKS into KV for this
					// issuer and sign their own tokens, so nothing reaches the network.
					AUTH0_DOMAIN: 'auth.test.example',
					AUTH0_AUDIENCE: 'https://api.kozeki.test',
				},
			},
		}),
	],
	test: {
		include: ['test/**/*.spec.ts'],
		setupFiles: ['./test/setup.ts'],
	},
});
