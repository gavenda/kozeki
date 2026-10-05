package dev.gavenda.kozeki.data.db

/**
 * Bookkeeping every syncable row carries so records can be synced later without a schema change:
 * rows are never hard-deleted ([deletedAt] is a tombstone) and a row is dirty when
 * [updatedAt] is newer than [syncedAt].
 */
data class SyncStamp(
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncedAt: Long? = null,
) {
    fun touched(now: Long): SyncStamp = copy(updatedAt = now)

    fun deleted(now: Long): SyncStamp = copy(updatedAt = now, deletedAt = now)

    companion object {
        fun created(now: Long): SyncStamp = SyncStamp(createdAt = now, updatedAt = now)
    }
}
