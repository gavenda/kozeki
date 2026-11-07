/** Bindings `wrangler types` does not know of: secrets, and what the tests add. */
interface Env {
	/** A Google Cloud API key with the Books API, set with `wrangler secret put`. Optional. */
	GOOGLE_BOOKS_API_KEY?: string;
	/** Test-only, set by `vitest.server.config.ts`. */
	TEST_MIGRATIONS?: unknown;
}
