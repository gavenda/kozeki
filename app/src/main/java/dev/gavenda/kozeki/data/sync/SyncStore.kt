package dev.gavenda.kozeki.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

/**
 * Where the sync stands, apart from the settings so a backup never carries it.
 *
 * Everything here belongs to [owner], the account the library on this device was last synced
 * with. When someone else signs in it all starts over (see [SyncEngine.adopt]).
 */
class SyncStore(context: Context) {

    private val store = context.applicationContext.syncStore

    /** The account whose library this device holds, or null before the first sign-in. */
    val owner: Flow<String?> = store.data.map { it[Keys.Owner] }.distinctUntilChanged()

    /** Whether the first sync with [owner] went all the way through, which is what lets the library open. */
    val firstSyncDone: Flow<Boolean> = store.data.map { it[Keys.FirstSyncDone] ?: false }.distinctUntilChanged()

    /** When the last sync finished, in epoch milliseconds. */
    val lastSyncedAt: Flow<Long?> = store.data.map { it[Keys.LastSyncedAt] }.distinctUntilChanged()

    suspend fun owner(): String? = owner.first()

    suspend fun cursor(): Long = store.data.first()[Keys.Cursor] ?: 0L

    suspend fun setCursor(cursor: Long) {
        store.edit { it[Keys.Cursor] = cursor }
    }

    /** Hands the device to [account]: nothing pulled, nothing sent, no covers known. */
    suspend fun startOver(account: String) {
        store.edit {
            it.clear()
            it[Keys.Owner] = account
        }
    }

    /** Pulls everything again from the start, for after a backup was put back. */
    suspend fun resetCursor() {
        store.edit { it.remove(Keys.Cursor) }
    }

    /** Whether the first sync has begun sending, after which its question is not asked again. */
    suspend fun firstSyncSending(): Boolean = store.data.first()[Keys.FirstSyncSending] ?: false

    suspend fun startedSending() {
        store.edit { it[Keys.FirstSyncSending] = true }
    }

    /**
     * Whether books entered twice before there was an account have been made one. Done once, after
     * a sync; from then on the first sync's question keeps it from happening.
     */
    suspend fun duplicatesMerged(): Boolean = store.data.first()[Keys.DuplicatesMerged] ?: false

    suspend fun mergedDuplicates() {
        store.edit { it[Keys.DuplicatesMerged] = true }
    }

    suspend fun finished(at: Long) {
        store.edit {
            it[Keys.FirstSyncDone] = true
            it[Keys.LastSyncedAt] = at
        }
    }

    /** Cover files the server is known to have, so they are not asked about again. */
    suspend fun uploadedCovers(): Set<String> = store.data.first()[Keys.UploadedCovers].orEmpty()

    suspend fun addUploadedCovers(names: Collection<String>) {
        if (names.isEmpty()) return
        store.edit { it[Keys.UploadedCovers] = it[Keys.UploadedCovers].orEmpty() + names }
    }

    private object Keys {
        val Owner = stringPreferencesKey("owner")
        val Cursor = longPreferencesKey("cursor")
        val FirstSyncDone = booleanPreferencesKey("first_sync_done")
        val LastSyncedAt = longPreferencesKey("last_synced_at")
        val UploadedCovers = stringSetPreferencesKey("uploaded_covers")
        val FirstSyncSending = booleanPreferencesKey("first_sync_sending")
        val DuplicatesMerged = booleanPreferencesKey("duplicates_merged")
    }
}
