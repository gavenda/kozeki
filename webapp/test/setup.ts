import { applyD1Migrations, env, type D1Migration } from 'cloudflare:test';
import { beforeEach } from 'vitest';
import { TABLE_NAMES, tableOf } from '../shared/tables';

const migrations = (env as unknown as { TEST_MIGRATIONS: D1Migration[] }).TEST_MIGRATIONS;

await applyD1Migrations(env.DB, migrations);

async function clearKv(namespace: KVNamespace): Promise<void> {
	let cursor: string | undefined;
	do {
		const page = await namespace.list({ cursor });
		await Promise.all(page.keys.map((key) => namespace.delete(key.name)));
		cursor = page.list_complete ? undefined : page.cursor;
	} while (cursor);
}

async function clearBucket(bucket: R2Bucket): Promise<void> {
	let cursor: string | undefined;
	do {
		const page = await bucket.list({ cursor });
		if (page.objects.length) await bucket.delete(page.objects.map((object) => object.key));
		cursor = page.truncated ? page.cursor : undefined;
	} while (cursor);
}

// One Miniflare instance is shared across a file's tests, so each starts from empty.
beforeEach(async () => {
	await env.DB.batch([...TABLE_NAMES.map((name) => env.DB.prepare(`DELETE FROM ${tableOf(name)}`)), env.DB.prepare('DELETE FROM users')]);
	await clearKv(env.CACHE);
	await clearBucket(env.COVERS);
});
