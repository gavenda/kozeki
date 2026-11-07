# Kozeki web app and API

The app's rules (voice, branding, what syncs) are in the root [CLAUDE.md](../CLAUDE.md) and [BRANDING.md](../BRANDING.md). This covers the Worker and the browser app. The setup follows the sister project Yuuka (`../yuuka/webapp`), so look there first for a pattern this one lacks.

## Commands

```bash
bun run dev         # Worker on :8788, Vite on :5173
bun run test        # browser suite (vitest) and API suite (workerd + D1)
bun run typecheck   # vue-tsc for the browser, tsc for server/
bun run format      # prettier: tabs, single quotes, 140 columns
```

Run `bun run typecheck` and `bun run test` before calling a change done.

## Shape

- **One table spec.** `shared/tables.ts` lists every synced table and its fields. The server builds its SQL, validation and wire mapping from it, and the browser's types follow it. A row travels as the Android Room entity serialises (camelCase, bookkeeping in `sync`); the column is the field in snake_case. `test/schema.spec.ts` checks every field has its column.
- **One write path.** Both apps change data only through `POST /api/sync`. A push is applied in one D1 batch: `users.rev` moves by the number of rows and each row takes its number in the same transaction, so a pull past a cursor can never miss a row that commits later with a lower number. Each table's rows go in as one JSON array read by one statement (`json_each`), so a request costs about 30 D1 queries however many rows it carries; the account is on the Free plan, which allows 50. The upsert keeps the stored row when its `updated_at` is newer, and every pushed row the server did not take whole comes back in the answer so the sender catches up.
- **A book has two clocks.** Its reader's columns (`PROGRESS_FIELDS`) win by `last_read_at`, the rest by `updated_at`, on the server (`upsert`), in the browser and on the phone (`mergeBook` in each). A page turned on the phone and an edit made on the web from a copy that had not seen it both survive. Anything that changes the reading position must move `lastReadAt`.
- **Nothing is hard-deleted.** `deleted_at` is a tombstone, so deletions sync like edits.
- **Every query is scoped by `user_id`**, the Auth0 subject from the verified token (`middleware/auth.ts`), never from the request.
- **Covers** are R2 objects at `covers/<subject>/<file>`. File names are unique per save (`<bookId>-<millis>.jpg`, as the phone names them), so objects never change and can be cached for good. Replaced covers are not cleaned up.
- **Hardcover** for the web is its own OAuth client with PKCE; the token stays in the browser. The Worker only forwards `/token`, `/revoke` and `/graphql`, because Hardcover does not answer other origins.

## Browser app

- `stores/library.ts` loads the whole library on sign-in and pulls again whenever the tab comes back into view. It is online only: the PWA caches the app shell, never the data.
- `lib/library.ts` is a port of the phone's write rules (`LibraryRepository`, `ShelfRepository`): changing state opens and closes read-throughs, deleting a book tombstones its children, and so on. Each operation runs in a `Tx` and returns the rows it changed, which `edit()` shows at once and pushes. Keep it in step with the Kotlin when either changes; `lib/library.spec.ts` covers the rules.
- `lib/stats.ts` ports `StatsRepository`: nothing is stored aggregated. Days are epoch days in the browser's zone, as the phone stores them in its own.
- Copy lives in `src/strings.ts` and follows the voice in BRANDING.md; where the phone shows the same thing, the words match `strings.xml`.
- Colours in `src/style.css` are printed from the Android theme (see BRANDING.md, "Colour"). Use the role classes, never palette scales.
- Icons are Material Symbols paths in `lib/icons.ts`, from the same source as the Android drawables (see the root CLAUDE.md).
