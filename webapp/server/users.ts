/**
 * Makes sure the signed-in subject has a `users` row, which holds the counter every write
 * takes its `rev` from. Steady state is the read alone; `INSERT OR IGNORE` lets the first
 * requests race here harmlessly.
 */
export async function ensureUser(env: Env, subject: string): Promise<void> {
	const existing = await env.DB.prepare('SELECT 1 AS present FROM users WHERE id = ?').bind(subject).first();
	if (existing) return;
	await env.DB.prepare('INSERT OR IGNORE INTO users (id, rev, created_at) VALUES (?, 0, ?)').bind(subject, Date.now()).run();
}
