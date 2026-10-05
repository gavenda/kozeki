package dev.gavenda.kozeki.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

enum class ReaderTheme { SYSTEM, LIGHT, SEPIA, DARK }

enum class ReaderFont { PUBLISHER, SERIF, SANS_SERIF, MONOSPACE }

/** How many requests went to a rate-limited source on [day], so the quota is visible in Settings. */
data class RequestCount(val day: Long, val count: Int)

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val metadataSource: Flow<MetadataSource> = store.data
        .map { prefs -> prefs[Keys.MetadataSource].toEnum(MetadataSource.GOOGLE_BOOKS) }
        .distinctUntilChanged()

    val dailyGoalMinutes: Flow<Int> = store.data
        .map { it[Keys.DailyGoalMinutes] ?: DEFAULT_DAILY_GOAL_MINUTES }
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

    val googleBooksRequests: Flow<RequestCount> = store.data
        .map { RequestCount(it[Keys.GoogleRequestsDay] ?: 0L, it[Keys.GoogleRequestsCount] ?: 0) }
        .distinctUntilChanged()

    /** ISO 4217 code last used for a purchase price, so the next one defaults to it. */
    val lastCurrency: Flow<String?> = store.data.map { it[Keys.LastCurrency] }.distinctUntilChanged()

    suspend fun setMetadataSource(source: MetadataSource) {
        store.edit { it[Keys.MetadataSource] = source.name }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        store.edit { it[Keys.DailyGoalMinutes] = minutes.coerceIn(1, 24 * 60) }
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

    suspend fun recordGoogleBooksRequest(day: Long) {
        store.edit { prefs ->
            val sameDay = prefs[Keys.GoogleRequestsDay] == day
            prefs[Keys.GoogleRequestsDay] = day
            prefs[Keys.GoogleRequestsCount] = if (sameDay) (prefs[Keys.GoogleRequestsCount] ?: 0) + 1 else 1
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val MetadataSource = stringPreferencesKey("metadata_source")
        val DailyGoalMinutes = intPreferencesKey("daily_goal_minutes")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val ReaderPreferences = stringPreferencesKey("reader_preferences")
        val GoogleRequestsDay = longPreferencesKey("google_requests_day")
        val GoogleRequestsCount = intPreferencesKey("google_requests_count")
        val LastCurrency = stringPreferencesKey("last_currency")
    }

    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 20
    }
}
