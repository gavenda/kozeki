package dev.gavenda.kozeki.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gavenda.kozeki.data.backup.DriveLink
import dev.gavenda.kozeki.data.backup.drive.DriveLinkStore
import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class ReaderPreferences(
    /** Multiplier on the publisher's base size. */
    val fontScale: Double = 1.0,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val font: ReaderFont = ReaderFont.PUBLISHER,
    /** Continuous scrolling instead of pages. */
    val scroll: Boolean = false,
    val justify: Boolean = false,
    /** Null leaves the publisher's line height alone. */
    val lineHeight: Double? = null,
)

/** Whether the app is light or dark, or leaves that to the device. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderTheme { SYSTEM, LIGHT, SEPIA, DARK }

enum class ReaderFont { PUBLISHER, SERIF, SANS_SERIF, MONOSPACE }

/**
 * The settings as they go into a backup. One that was never changed is null, and putting the
 * backup back leaves it as it is on the device.
 */
@Serializable
data class SettingsSnapshot(
    val dailyGoalMinutes: Int? = null,
    val themeMode: ThemeMode? = null,
    val readerPreferences: ReaderPreferences? = null,
    val lastCurrency: String? = null,
    val librarySort: String? = null,
    val librarySortReversed: Boolean? = null,
    val libraryDisplay: String? = null,
    val searchSource: MetadataSource? = null,
)

class SettingsRepository(context: Context) : DriveLinkStore {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val dailyGoalMinutes: Flow<Int> = store.data
        .map { it[Keys.DailyGoalMinutes] ?: DEFAULT_DAILY_GOAL_MINUTES }
        .distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = store.data
        .map { prefs -> ThemeMode.entries.find { it.name == prefs[Keys.ThemeMode] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    val readerPreferences: Flow<ReaderPreferences> = store.data
        .map { prefs ->
            prefs[Keys.ReaderPreferences]
                ?.let { runCatching { json.decodeFromString<ReaderPreferences>(it) }.getOrNull() }
                ?: ReaderPreferences()
        }
        .distinctUntilChanged()

    /** ISO 4217 code last used for a purchase price, so the next one defaults to it. */
    val lastCurrency: Flow<String?> = store.data.map { it[Keys.LastCurrency] }.distinctUntilChanged()

    /** Name of the order the library was last put in, or null while the user has not picked one. */
    val librarySort: Flow<String?> = store.data.map { it[Keys.LibrarySort] }.distinctUntilChanged()

    /** Whether the library's order is turned the other way round from its usual direction. */
    val librarySortReversed: Flow<Boolean> = store.data
        .map { it[Keys.LibrarySortReversed] ?: false }
        .distinctUntilChanged()

    /** Name of the way the library's books were last laid out, or null while the user has not picked one. */
    val libraryDisplay: Flow<String?> = store.data.map { it[Keys.LibraryDisplay] }.distinctUntilChanged()

    /** Name of the kind of book the library is showing, or null while the user has not picked one. */
    val libraryKind: Flow<String?> = store.data.map { it[Keys.LibraryKind] }.distinctUntilChanged()

    /**
     * Whether the library keeps comics apart from the books with an EPUB or a paper copy, showing
     * one kind at a time. On unless the user turned it off.
     */
    val separateLibraryKinds: Flow<Boolean> =
        store.data.map { it[Keys.SeparateLibraryKinds] ?: true }.distinctUntilChanged()

    /** Hours between the checks of followed comics for new chapters, or 0 for none. */
    val updateIntervalHours: Flow<Int> =
        store.data.map { it[Keys.UpdateIntervalHours] ?: DEFAULT_UPDATE_INTERVAL_HOURS }.distinctUntilChanged()

    // Opened here, ahead of its first use, so that reading it later does not wait for the disk.
    private val searchPreferences = context.applicationContext.getSharedPreferences("search", Context.MODE_PRIVATE)

    /**
     * The catalogue the online search was last pointed at: Hardcover until the user picks another.
     * A shared preference rather than a part of the store, so the search screen can read it at
     * once and open with the right source already picked.
     */
    var searchSource: MetadataSource
        get() {
            val name = searchPreferences.getString(SEARCH_SOURCE, null)
            return MetadataSource.named(name) ?: MetadataSource.HARDCOVER
        }
        set(value) = searchPreferences.edit { putString(SEARCH_SOURCE, value.name) }

    /**
     * The book catalogue the online search was last pointed at, so that going back from comics to
     * books returns to it. Until one is saved, the last search source when that is a book one.
     */
    var bookSearchSource: MetadataSource
        get() {
            val saved = MetadataSource.named(searchPreferences.getString(BOOK_SOURCE, null))
            return saved ?: searchSource.takeIf { it in MetadataSource.matchable } ?: MetadataSource.HARDCOVER
        }
        set(value) = searchPreferences.edit { putString(BOOK_SOURCE, value.name) }

    /**
     * The ID of the comic source the online search of comics goes to, or 0 while the user has not
     * picked one. Kept beside [searchSource] for the same reason.
     */
    var comicSourceId: Long
        get() = searchPreferences.getLong(COMIC_SOURCE, searchPreferences.getLong(LEGACY_COMIC_SOURCE, 0L))
        set(value) = searchPreferences.edit { putLong(COMIC_SOURCE, value) }

    /**
     * The Google account backups go to, or null while nobody is signed in. It says where the
     * backup is, not what is in the library, so it is no part of a [SettingsSnapshot].
     */
    override val driveLink: Flow<DriveLink?> = store.data
        .map { prefs -> prefs[Keys.DriveLink]?.let { runCatching { json.decodeFromString<DriveLink>(it) }.getOrNull() } }
        .distinctUntilChanged()

    override suspend fun setDriveLink(link: DriveLink?) {
        store.edit { prefs ->
            if (link == null) prefs.remove(Keys.DriveLink) else prefs[Keys.DriveLink] = json.encodeToString(link)
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = store.data.first()
        return SettingsSnapshot(
            dailyGoalMinutes = prefs[Keys.DailyGoalMinutes],
            themeMode = ThemeMode.entries.find { it.name == prefs[Keys.ThemeMode] },
            readerPreferences = prefs[Keys.ReaderPreferences]
                ?.let { runCatching { json.decodeFromString<ReaderPreferences>(it) }.getOrNull() },
            lastCurrency = prefs[Keys.LastCurrency],
            librarySort = prefs[Keys.LibrarySort],
            librarySortReversed = prefs[Keys.LibrarySortReversed],
            libraryDisplay = prefs[Keys.LibraryDisplay],
            searchSource = MetadataSource.named(searchPreferences.getString(SEARCH_SOURCE, null)),
        )
    }

    /** Takes over what [snapshot] holds, in one write. */
    suspend fun restore(snapshot: SettingsSnapshot) {
        store.edit { prefs ->
            snapshot.dailyGoalMinutes?.let { prefs[Keys.DailyGoalMinutes] = it.coerceIn(1, 24 * 60) }
            snapshot.themeMode?.let { prefs[Keys.ThemeMode] = it.name }
            snapshot.readerPreferences?.let { prefs[Keys.ReaderPreferences] = json.encodeToString(it) }
            snapshot.lastCurrency?.let { prefs[Keys.LastCurrency] = it }
            snapshot.librarySort?.let { prefs[Keys.LibrarySort] = it }
            snapshot.librarySortReversed?.let { prefs[Keys.LibrarySortReversed] = it }
            snapshot.libraryDisplay?.let { prefs[Keys.LibraryDisplay] = it }
        }
        snapshot.searchSource?.let { searchSource = it }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        store.edit { it[Keys.DailyGoalMinutes] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.ThemeMode] = mode.name }
    }

    suspend fun setReaderPreferences(preferences: ReaderPreferences) {
        store.edit { it[Keys.ReaderPreferences] = json.encodeToString(preferences) }
    }

    suspend fun setLastCurrency(code: String) {
        store.edit { it[Keys.LastCurrency] = code }
    }

    suspend fun setLibrarySort(name: String, reversed: Boolean) {
        store.edit {
            it[Keys.LibrarySort] = name
            it[Keys.LibrarySortReversed] = reversed
        }
    }

    suspend fun setLibraryKind(name: String) {
        store.edit { it[Keys.LibraryKind] = name }
    }

    suspend fun setSeparateLibraryKinds(separate: Boolean) {
        store.edit { it[Keys.SeparateLibraryKinds] = separate }
    }

    suspend fun setUpdateIntervalHours(hours: Int) {
        store.edit { it[Keys.UpdateIntervalHours] = hours.coerceAtLeast(0) }
    }

    suspend fun setLibraryDisplay(name: String) {
        store.edit { it[Keys.LibraryDisplay] = name }
    }

    private object Keys {
        val DailyGoalMinutes = intPreferencesKey("daily_goal_minutes")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ReaderPreferences = stringPreferencesKey("reader_preferences")
        val LastCurrency = stringPreferencesKey("last_currency")
        val LibrarySort = stringPreferencesKey("library_sort")
        val LibrarySortReversed = booleanPreferencesKey("library_sort_reversed")
        val LibraryDisplay = stringPreferencesKey("library_display")
        val LibraryKind = stringPreferencesKey("library_kind")
        val SeparateLibraryKinds = booleanPreferencesKey("separate_library_kinds")
        val DriveLink = stringPreferencesKey("drive_link")
        val UpdateIntervalHours = intPreferencesKey("update_interval_hours")
    }

    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 20
        const val DEFAULT_UPDATE_INTERVAL_HOURS = 12
        private const val SEARCH_SOURCE = "source"
        private const val COMIC_SOURCE = "comic_source"
        private const val BOOK_SOURCE = "book_source"

        /** Where [comicSourceId] was kept before comics were called comics. */
        private const val LEGACY_COMIC_SOURCE = "manga_source"
    }
}
