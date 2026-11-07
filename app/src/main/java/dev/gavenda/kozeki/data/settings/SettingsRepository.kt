package dev.gavenda.kozeki.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gavenda.kozeki.data.backup.DriveLink
import dev.gavenda.kozeki.data.backup.drive.DriveLinkStore
import dev.gavenda.kozeki.data.backup.local.LocalBackupConfig
import dev.gavenda.kozeki.data.backup.local.LocalBackupStore
import dev.gavenda.kozeki.data.metadata.BrowseOrder
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.sync.SettingRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    val searchDisplay: String? = null,
    val searchSource: MetadataSource? = null,
)

class SettingsRepository(context: Context) : DriveLinkStore, LocalBackupStore {

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

    /** Name of the way the results of the online search were last laid out, or null while the user has not picked one. */
    val searchDisplay: Flow<String?> = store.data.map { it[Keys.SearchDisplay] }.distinctUntilChanged()

    /** Name of the kind of book the library is showing, or null while the user has not picked one. */
    val libraryKind: Flow<String?> = store.data.map { it[Keys.LibraryKind] }.distinctUntilChanged()

    /**
     * The series and shelves of the library folded away under their headers, by the keys the library
     * gives them. How the library looks on this device rather than what is in it, so no part of a backup.
     */
    val collapsedLibraryGroups: Flow<Set<String>> =
        store.data.map { it[Keys.CollapsedLibraryGroups].orEmpty() }.distinctUntilChanged()

    /**
     * The languages of an extension, each as [comicLanguageKey] gives it, whose sources the online
     * search of comics leaves out. Every language is shown until the user switches one off. Which
     * extensions are installed is up to each device, so this is no part of a backup either.
     */
    val hiddenComicLanguages: Flow<Set<String>> =
        store.data.map { it[Keys.HiddenComicLanguages].orEmpty() }.distinctUntilChanged()

    /**
     * The index URLs of the repositories of Mihon extensions the user added, in the order added.
     * Kept on this device only, as the extensions themselves are.
     */
    val extensionRepos: Flow<List<String>> = store.data
        .map { prefs ->
            prefs[Keys.ExtensionRepos]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                .orEmpty()
        }
        .distinctUntilChanged()

    suspend fun addExtensionRepo(url: String) = editExtensionRepos { if (url in it) it else it + url }

    suspend fun removeExtensionRepo(url: String) = editExtensionRepos { it - url }

    private suspend fun editExtensionRepos(change: (List<String>) -> List<String>) {
        store.edit { prefs ->
            val now = prefs[Keys.ExtensionRepos]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                .orEmpty()
            prefs[Keys.ExtensionRepos] = json.encodeToString(change(now))
        }
    }

    /** Hours between the checks of followed comics for new chapters, or 0 for none. */
    val updateIntervalHours: Flow<Int> =
        store.data.map { it[Keys.UpdateIntervalHours] ?: DEFAULT_UPDATE_INTERVAL_HOURS }.distinctUntilChanged()

    /** Whether a comic's chapter is downloaded when it is opened, rather than only read from its source. On until switched off. */
    val downloadWhileReading: Flow<Boolean> =
        store.data.map { it[Keys.DownloadWhileReading] ?: true }.distinctUntilChanged()

    /** How many megabytes the pages of chapters read without downloading may take up before the oldest go. */
    val temporaryPagesMegabytes: Flow<Int> =
        store.data.map { it[Keys.TemporaryPagesMegabytes] ?: DEFAULT_TEMPORARY_PAGES_MB }.distinctUntilChanged()

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

    /** The list the online search of books shows with nothing typed, or null until one is picked. */
    var bookBrowseOrder: BrowseOrder?
        get() = BrowseOrder.named(searchPreferences.getString(BOOK_BROWSE, null))
        set(value) = searchPreferences.edit { putString(BOOK_BROWSE, value?.name) }

    /** The list the online search of comics shows with nothing typed, or null until one is picked. */
    var comicBrowseOrder: BrowseOrder?
        get() = BrowseOrder.named(searchPreferences.getString(COMIC_BROWSE, null))
        set(value) = searchPreferences.edit { putString(COMIC_BROWSE, value?.name) }

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

    /**
     * The folder local backups go to, how often, and how the last one went. The folder is this
     * device's, so this is no part of a [SettingsSnapshot] either.
     */
    override val localBackup: Flow<LocalBackupConfig> = store.data
        .map { prefs ->
            prefs[Keys.LocalBackup]?.let { runCatching { json.decodeFromString<LocalBackupConfig>(it) }.getOrNull() }
                ?: LocalBackupConfig()
        }
        .distinctUntilChanged()

    override suspend fun updateLocalBackup(transform: (LocalBackupConfig) -> LocalBackupConfig) {
        store.edit { prefs ->
            val now = prefs[Keys.LocalBackup]?.let { runCatching { json.decodeFromString<LocalBackupConfig>(it) }.getOrNull() }
                ?: LocalBackupConfig()
            prefs[Keys.LocalBackup] = json.encodeToString(transform(now))
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
            searchDisplay = prefs[Keys.SearchDisplay],
            searchSource = MetadataSource.named(searchPreferences.getString(SEARCH_SOURCE, null)),
        )
    }

    /** Takes over what [snapshot] holds, in one write. */
    suspend fun restore(snapshot: SettingsSnapshot) {
        store.edit { prefs ->
            snapshot.dailyGoalMinutes?.let { prefs.setShared(dailyGoalSetting, it.coerceIn(1, 24 * 60)) }
            snapshot.themeMode?.let { prefs[Keys.ThemeMode] = it.name }
            snapshot.readerPreferences?.let { prefs[Keys.ReaderPreferences] = json.encodeToString(it) }
            snapshot.lastCurrency?.let { prefs.setShared(lastCurrencySetting, it) }
            snapshot.librarySort?.let { prefs[Keys.LibrarySort] = it }
            snapshot.librarySortReversed?.let { prefs[Keys.LibrarySortReversed] = it }
            snapshot.libraryDisplay?.let { prefs[Keys.LibraryDisplay] = it }
            snapshot.searchDisplay?.let { prefs[Keys.SearchDisplay] = it }
        }
        snapshot.searchSource?.let { searchSource = it }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        store.edit { it.setShared(dailyGoalSetting, minutes.coerceIn(1, 24 * 60)) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.ThemeMode] = mode.name }
    }

    suspend fun setReaderPreferences(preferences: ReaderPreferences) {
        store.edit { it[Keys.ReaderPreferences] = json.encodeToString(preferences) }
    }

    suspend fun setLastCurrency(code: String) {
        store.edit { it.setShared(lastCurrencySetting, code) }
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

    /** Folds the group under [key] away, or opens it again. */
    suspend fun setLibraryGroupCollapsed(key: String, collapsed: Boolean) {
        store.edit { prefs ->
            val now = prefs[Keys.CollapsedLibraryGroups].orEmpty()
            prefs[Keys.CollapsedLibraryGroups] = if (collapsed) now + key else now - key
        }
    }

    suspend fun setUpdateIntervalHours(hours: Int) {
        store.edit { it[Keys.UpdateIntervalHours] = hours.coerceAtLeast(0) }
    }

    suspend fun setTemporaryPagesMegabytes(megabytes: Int) {
        store.edit { it[Keys.TemporaryPagesMegabytes] = megabytes.coerceAtLeast(1) }
    }

    suspend fun setDownloadWhileReading(on: Boolean) {
        store.edit { it[Keys.DownloadWhileReading] = on }
    }

    /** Shows or leaves out the sources of the extension [packageName] in [language] ("en", "all"). */
    suspend fun setComicLanguageShown(packageName: String, language: String, shown: Boolean) {
        val key = comicLanguageKey(packageName, language)
        store.edit { prefs ->
            val now = prefs[Keys.HiddenComicLanguages].orEmpty()
            prefs[Keys.HiddenComicLanguages] = if (shown) now - key else now + key
        }
    }

    suspend fun setLibraryDisplay(name: String) {
        store.edit { it[Keys.LibraryDisplay] = name }
    }

    suspend fun setSearchDisplay(name: String) {
        store.edit { it[Keys.SearchDisplay] = name }
    }

    // ---- Settings the web app shares ----------------------------------------------------------

    /**
     * One of the settings that sync with the account. Each keeps when it was last changed here and
     * when it was last sent or received, as a row of the library does.
     */
    private class Shared<T>(
        val name: String,
        val key: Preferences.Key<T>,
        val toJson: (T) -> JsonElement,
        val fromJson: (JsonElement) -> T?,
    ) {
        val updatedAt = longPreferencesKey("${key.name}_updated_at")
        val syncedAt = longPreferencesKey("${key.name}_synced_at")
    }

    private val dailyGoalSetting = Shared(
        "dailyGoalMinutes", Keys.DailyGoalMinutes, { JsonPrimitive(it) },
        { element -> element.jsonPrimitive.intOrNull?.coerceIn(1, 24 * 60) },
    )
    private val lastCurrencySetting = Shared(
        "lastCurrency", Keys.LastCurrency, { JsonPrimitive(it) },
        { element -> element.jsonPrimitive.contentOrNull?.takeIf { it.length == 3 } },
    )
    private val shared = listOf(dailyGoalSetting, lastCurrencySetting)

    private fun <T> MutablePreferences.setShared(setting: Shared<T>, value: T) {
        if (this[setting.key] == value) return
        this[setting.key] = value
        this[setting.updatedAt] = System.currentTimeMillis()
    }

    /** The shared settings changed here since they were last sent. */
    suspend fun unsentSharedSettings(): List<SettingRow> {
        val prefs = store.data.first()
        return shared.mapNotNull { setting -> prefs.unsent(setting) }
    }

    private fun <T> Preferences.unsent(setting: Shared<T>): SettingRow? {
        val value = this[setting.key] ?: return null
        // One set before settings synced counts as the oldest there is, so the web's wins over it.
        val updatedAt = this[setting.updatedAt] ?: NEVER_STAMPED
        if (this[setting.syncedAt] == updatedAt) return null
        return SettingRow(setting.name, setting.toJson(value), SyncStamp(createdAt = updatedAt, updatedAt = updatedAt))
    }

    suspend fun sentSharedSetting(name: String, updatedAt: Long) {
        val setting = shared.find { it.name == name } ?: return
        store.edit {
            if ((it[setting.updatedAt] ?: NEVER_STAMPED) != updatedAt) return@edit
            it[setting.updatedAt] = updatedAt
            it[setting.syncedAt] = updatedAt
        }
    }

    /** Takes a shared setting changed elsewhere, unless it was changed here since. */
    suspend fun receiveSharedSetting(row: SettingRow) {
        val setting = shared.find { it.name == row.key } ?: return
        store.edit { it.receive(setting, row) }
    }

    private fun <T> MutablePreferences.receive(setting: Shared<T>, row: SettingRow) {
        val local = this[setting.updatedAt]
        if (local != null && local > row.sync.updatedAt) return
        val value = row.value?.let { runCatching { setting.fromJson(it) }.getOrNull() } ?: return
        this[setting.key] = value
        this[setting.updatedAt] = row.sync.updatedAt
        this[setting.syncedAt] = row.sync.updatedAt
    }

    /** Sends every shared setting again, for after a backup was put back. */
    suspend fun unsendSharedSettings() {
        store.edit { prefs -> shared.forEach { prefs.remove(it.syncedAt) } }
    }

    private object Keys {
        val DailyGoalMinutes = intPreferencesKey("daily_goal_minutes")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ReaderPreferences = stringPreferencesKey("reader_preferences")
        val LastCurrency = stringPreferencesKey("last_currency")
        val LibrarySort = stringPreferencesKey("library_sort")
        val LibrarySortReversed = booleanPreferencesKey("library_sort_reversed")
        val DownloadWhileReading = booleanPreferencesKey("comic_download_while_reading")
        val TemporaryPagesMegabytes = intPreferencesKey("comic_temporary_pages_mb")
        val LibraryDisplay = stringPreferencesKey("library_display")
        val SearchDisplay = stringPreferencesKey("search_display")
        val LibraryKind = stringPreferencesKey("library_kind")
        val CollapsedLibraryGroups = stringSetPreferencesKey("library_collapsed_groups")
        val DriveLink = stringPreferencesKey("drive_link")
        val LocalBackup = stringPreferencesKey("local_backup")
        val UpdateIntervalHours = intPreferencesKey("update_interval_hours")
        val HiddenComicLanguages = stringSetPreferencesKey("comic_hidden_languages")
        val ExtensionRepos = stringPreferencesKey("comic_extension_repos")
    }

    companion object {
        /** How a language of one extension is kept in [hiddenComicLanguages]. */
        fun comicLanguageKey(packageName: String, language: String): String = "$packageName/$language"

        const val DEFAULT_DAILY_GOAL_MINUTES = 20
        private const val NEVER_STAMPED = 1L
        const val DEFAULT_UPDATE_INTERVAL_HOURS = 12
        const val DEFAULT_TEMPORARY_PAGES_MB = 250
        private const val SEARCH_SOURCE = "source"
        private const val COMIC_SOURCE = "comic_source"
        private const val BOOK_SOURCE = "book_source"
        private const val BOOK_BROWSE = "book_browse"
        private const val COMIC_BROWSE = "comic_browse"

        /** Where [comicSourceId] was kept before comics were called comics. */
        private const val LEGACY_COMIC_SOURCE = "manga_source"
    }
}
