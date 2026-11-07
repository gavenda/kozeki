import { defineStore } from 'pinia';
import { computed, reactive, ref } from 'vue';
import { mergeBook, TABLE_NAMES, type SyncResponse, type TableName } from '../../shared/tables';
import { apiJson, ApiError } from '@/lib/http';
import { alive, keyOf, Tx, type Tables } from '@/lib/library';
import type { Book, Changes, Rows, Setting } from '@/types';

const MAX_PUSH = 200;

function emptyTables(): Tables {
	return Object.fromEntries(TABLE_NAMES.map((name) => [name, new Map()])) as unknown as Tables;
}

type AnyRow = Rows[TableName][number];

/**
 * The library as the server holds it, loaded whole when the app opens and kept current from there.
 *
 * Edits run through [edit]: the change shows at once, goes to the server through the same
 * `/api/sync` the phone uses, and the answer is taken in. Where the server kept a newer version of
 * a row, that version comes back and replaces the edit, as the phone would see it.
 */
export const useLibrary = defineStore('library', () => {
	const tables = reactive(emptyTables()) as Tables;
	const cursor = ref(0);
	const loaded = ref(false);
	const loading = ref(false);
	const error = ref<string | null>(null);
	/** When an exchange with the server last went through, for Settings. */
	const lastSyncedAt = ref<number | null>(null);
	let queue: Promise<void> = Promise.resolve();

	/**
	 * Takes rows from the server in, each unless the one here was edited later; a book's reading
	 * position is judged apart from the rest of it, as the server judges it (see `mergeBook`).
	 */
	function take(rows: Changes): void {
		for (const name of TABLE_NAMES) {
			for (const row of (rows[name] ?? []) as AnyRow[]) {
				const map = tables[name] as Map<string, AnyRow>;
				const key = keyOf(name, row as never);
				const current = map.get(key);
				if (name === 'books') {
					const merged = mergeBook(current as Book | undefined, row as Book);
					if (merged) map.set(key, merged.book);
				} else if (!current || row.sync.updatedAt >= current.sync.updatedAt) {
					map.set(key, row);
				}
			}
		}
	}

	/** Drops everything and pulls the whole library again, for when what is on screen cannot be trusted. */
	async function reload(): Promise<void> {
		for (const name of TABLE_NAMES) tables[name].clear();
		cursor.value = 0;
		loaded.value = false;
		await refresh();
	}

	async function exchange(push: Changes): Promise<boolean> {
		const answer = await apiJson<SyncResponse>('/sync', { cursor: cursor.value, push });
		take(answer.rows as Changes);
		cursor.value = answer.cursor;
		lastSyncedAt.value = Date.now();
		return answer.more;
	}

	/** Brings in everything that changed since the last pull. */
	async function refresh(): Promise<void> {
		if (loading.value) return;
		loading.value = true;
		try {
			while (await exchange({}));
			loaded.value = true;
			error.value = null;
		} catch (e) {
			error.value = e instanceof ApiError ? e.message : String(e);
		} finally {
			loading.value = false;
		}
	}

	function chunks(changes: Changes): Changes[] {
		const out: Changes[] = [];
		let current: Changes = {};
		let size = 0;
		for (const name of TABLE_NAMES) {
			for (const row of (changes[name] ?? []) as AnyRow[]) {
				if (size === MAX_PUSH) {
					out.push(current);
					current = {};
					size = 0;
				}
				((current[name] ??= []) as AnyRow[]).push(row);
				size++;
			}
		}
		if (size) out.push(current);
		return out;
	}

	/**
	 * Runs [operation] over the library and saves what it changed. The change shows at once; pushes
	 * go one after another, in the order the edits were made. When one fails, part of an edit may
	 * have landed and part not, so the library is pulled again whole and shows what the server has.
	 */
	function edit<T>(operation: (tx: Tx) => T): T {
		const tx = new Tx(tables);
		const result = operation(tx);
		const changes = tx.changes();
		for (const name of TABLE_NAMES) {
			for (const row of (changes[name] ?? []) as AnyRow[]) (tables[name] as Map<string, AnyRow>).set(keyOf(name, row as never), row);
		}

		queue = queue.then(async () => {
			try {
				for (const chunk of chunks(changes)) await exchange(chunk);
				error.value = null;
			} catch (e) {
				const reason = e instanceof ApiError ? e.message : String(e);
				await reload();
				error.value = reason;
			}
		});
		return result;
	}

	/** Waits for the edits made so far to reach the server. */
	const settled = () => queue;

	const books = computed<Book[]>(() => [...tables.books.values()].filter(alive));
	const shelves = computed(() => [...tables.shelves.values()].filter(alive).sort((a, b) => a.name.localeCompare(b.name)));

	/** The books on each shelf, by shelf. */
	const members = computed(() => {
		const out = new Map<string, Set<string>>();
		for (const row of tables.shelfBooks.values()) {
			if (!alive(row)) continue;
			(out.get(row.shelfId) ?? out.set(row.shelfId, new Set()).get(row.shelfId)!).add(row.bookId);
		}
		return out;
	});
	/** How many of [bookIds] each shelf holds, for the shelf checklist. */
	const shelfCounts = (bookIds: string[]) =>
		new Map(shelves.value.map((shelf) => [shelf.id, bookIds.filter((id) => members.value.get(shelf.id)?.has(id)).length]));

	/** The places books were bought at before, offered again in the purchase dialog. */
	const purchaseLocations = computed(() =>
		[...new Set(books.value.flatMap((book) => (book.purchaseLocation ? [book.purchaseLocation] : [])))].sort((a, b) => a.localeCompare(b)),
	);

	function setting<T>(key: string, fallback: T): T {
		const row = tables.settings.get(key) as Setting | undefined;
		return (row?.value as T | undefined) ?? fallback;
	}

	const lastCurrency = computed(() => setting<string>('lastCurrency', 'USD'));

	return {
		tables,
		cursor,
		loaded,
		loading,
		error,
		lastSyncedAt,
		refresh,
		edit,
		settled,
		books,
		shelves,
		members,
		shelfCounts,
		purchaseLocations,
		lastCurrency,
		setting,
	};
});
