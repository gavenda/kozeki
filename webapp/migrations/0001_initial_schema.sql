-- Kozeki's book data, one user's rows apart from another's by `user_id` (the Auth0 subject).
--
-- Each synced table mirrors a Room table of the Android app; the columns are the
-- entity's fields in snake_case, as `shared/tables.ts` lists them. Rows are never
-- deleted by the apps: `deleted_at` is a tombstone, so a deletion syncs like any
-- other change. Times are epoch milliseconds, days are epoch days, as on the phone.
--
-- `rev` orders changes for pulling. Every write takes the next number from the
-- user's counter in `users.rev`, so "everything after rev N" is exactly what a
-- device has not seen yet. Child rows carry no foreign keys to their book: a
-- push may land a session before its book on a retried request, and a missing
-- parent must not wedge the sync for good.

CREATE TABLE users (
	id TEXT PRIMARY KEY,
	rev INTEGER NOT NULL DEFAULT 0,
	created_at INTEGER NOT NULL
);

CREATE TABLE books (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	title TEXT NOT NULL,
	subtitle TEXT,
	authors TEXT,
	author_refs TEXT,
	description TEXT,
	publisher TEXT,
	series TEXT,
	series_number REAL,
	published_date TEXT,
	language TEXT,
	page_count INTEGER,
	isbn_10 TEXT,
	isbn_13 TEXT,
	categories TEXT,
	cover_file TEXT,
	cover_url TEXT,
	custom_cover INTEGER NOT NULL DEFAULT 0 CHECK (custom_cover IN (0, 1)),
	details_edited INTEGER NOT NULL DEFAULT 0 CHECK (details_edited IN (0, 1)),
	state TEXT,
	acquisition TEXT,
	is_favorite INTEGER NOT NULL DEFAULT 0 CHECK (is_favorite IN (0, 1)),
	rating REAL,
	purchase_price_minor INTEGER,
	purchase_currency TEXT,
	purchased_on INTEGER,
	purchase_location TEXT,
	purchase_memo TEXT,
	source TEXT,
	source_id TEXT,
	source_url TEXT,
	match_status TEXT,
	file_hash TEXT,
	file_size INTEGER,
	original_file_name TEXT,
	locator TEXT,
	progression REAL,
	position INTEGER,
	position_count INTEGER,
	chapter_title TEXT,
	chapter_index INTEGER,
	chapter_count INTEGER,
	last_read_at INTEGER,
	physical_page INTEGER,
	physical_page_count INTEGER,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX books_rev ON books (user_id, rev);

CREATE TABLE read_throughs (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	book_id TEXT NOT NULL,
	number INTEGER NOT NULL,
	started_at INTEGER NOT NULL,
	finished_at INTEGER,
	outcome TEXT,
	finished_on INTEGER,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX read_throughs_rev ON read_throughs (user_id, rev);

CREATE TABLE reading_sessions (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	book_id TEXT NOT NULL,
	read_through_id TEXT,
	started_at INTEGER NOT NULL,
	ended_at INTEGER NOT NULL,
	duration_ms INTEGER NOT NULL,
	day INTEGER NOT NULL,
	start_progression REAL NOT NULL,
	end_progression REAL NOT NULL,
	start_position INTEGER,
	end_position INTEGER,
	pages INTEGER,
	chapters INTEGER,
	start_chapter TEXT,
	end_chapter TEXT,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX reading_sessions_rev ON reading_sessions (user_id, rev);

CREATE TABLE physical_readings (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	book_id TEXT NOT NULL,
	read_through_id TEXT,
	recorded_at INTEGER NOT NULL,
	day INTEGER NOT NULL,
	start_page INTEGER NOT NULL,
	end_page INTEGER NOT NULL,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX physical_readings_rev ON physical_readings (user_id, rev);

CREATE TABLE notes (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	book_id TEXT NOT NULL,
	text TEXT NOT NULL,
	chapter_title TEXT,
	progression REAL,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX notes_rev ON notes (user_id, rev);

CREATE TABLE yearly_goals (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	year INTEGER NOT NULL,
	books INTEGER NOT NULL,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, year)
);

CREATE INDEX yearly_goals_rev ON yearly_goals (user_id, rev);

CREATE TABLE shelves (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	id TEXT NOT NULL,
	name TEXT NOT NULL,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, id)
);

CREATE INDEX shelves_rev ON shelves (user_id, rev);

CREATE TABLE shelf_books (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	shelf_id TEXT NOT NULL,
	book_id TEXT NOT NULL,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, shelf_id, book_id)
);

CREATE INDEX shelf_books_rev ON shelf_books (user_id, rev);

CREATE TABLE settings (
	user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	key TEXT NOT NULL,
	value TEXT,
	created_at INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted_at INTEGER,
	rev INTEGER NOT NULL,
	PRIMARY KEY (user_id, key)
);

CREATE INDEX settings_rev ON settings (user_id, rev);
