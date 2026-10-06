package dev.gavenda.kozeki.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val dailyGoalMinutes: Flow<Int> = store.data
        .map { it[Keys.DailyGoalMinutes] ?: DEFAULT_DAILY_GOAL_MINUTES }
        .distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = store.data
        .map { prefs -> ThemeMode.entries.find { it.name == prefs[Keys.ThemeMode] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    /** Whether the theme follows the wallpaper instead of the app's own seed colour. */
    val dynamicColor: Flow<Boolean> = store.data
        .map { it[Keys.DynamicColor] ?: true }
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

    /** Name of the way the library's books were last laid out, or null while the user has not picked one. */
    val libraryDisplay: Flow<String?> = store.data.map { it[Keys.LibraryDisplay] }.distinctUntilChanged()

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
            return MetadataSource.entries.find { it.name == name } ?: MetadataSource.HARDCOVER
        }
        set(value) = searchPreferences.edit { putString(SEARCH_SOURCE, value.name) }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        store.edit { it[Keys.DailyGoalMinutes] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.ThemeMode] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.DynamicColor] = enabled }
    }

    suspend fun setReaderPreferences(preferences: ReaderPreferences) {
        store.edit { it[Keys.ReaderPreferences] = json.encodeToString(preferences) }
    }

    suspend fun setLastCurrency(code: String) {
        store.edit { it[Keys.LastCurrency] = code }
    }

    suspend fun setLibrarySort(name: String) {
        store.edit { it[Keys.LibrarySort] = name }
    }

    suspend fun setLibraryDisplay(name: String) {
        store.edit { it[Keys.LibraryDisplay] = name }
    }

    private object Keys {
        val DailyGoalMinutes = intPreferencesKey("daily_goal_minutes")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val ReaderPreferences = stringPreferencesKey("reader_preferences")
        val LastCurrency = stringPreferencesKey("last_currency")
        val LibrarySort = stringPreferencesKey("library_sort")
        val LibraryDisplay = stringPreferencesKey("library_display")
    }

    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 20
        private const val SEARCH_SOURCE = "source"
    }
}
