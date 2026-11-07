package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.db.BookEntity

/**
 * A book's version for the sync: the later of its two clocks. Its reader's columns move
 * lastReadAt only, everything else moves updatedAt (see `PROGRESS_FIELDS` in webapp/shared/tables.ts).
 */
val BookEntity.version: Long get() = maxOf(sync.updatedAt, lastReadAt ?: 0L)

/**
 * [local] and [remote] put together, as the server puts them: the reader's columns from whichever
 * was read later, the rest from whichever was edited later. Null when [local] is later on both
 * counts, so there is nothing to take.
 *
 * The result counts as sent only when it is the remote row whole. With anything of [local] in it,
 * it is left waiting, so the server gets that part.
 */
fun mergeBook(local: BookEntity?, remote: BookEntity): BookEntity? {
    val received = remote.copy(sync = remote.sync.copy(syncedAt = remote.version))
    if (local == null) return received
    val details = remote.sync.updatedAt >= local.sync.updatedAt
    val progress = (remote.lastReadAt ?: 0L) >= (local.lastReadAt ?: 0L)
    return when {
        details && progress -> received
        details -> remote.withReaderOf(local)
        progress -> local.withReaderOf(remote).copy(sync = local.sync.copy(syncedAt = null))
        else -> null
    }.let { merged ->
        merged?.copy(sync = merged.sync.copy(updatedAt = maxOf(local.sync.updatedAt, remote.sync.updatedAt)))
    }
}

private fun BookEntity.withReaderOf(other: BookEntity): BookEntity = copy(
    locator = other.locator,
    progression = other.progression,
    position = other.position,
    positionCount = other.positionCount,
    chapterTitle = other.chapterTitle,
    chapterIndex = other.chapterIndex,
    chapterCount = other.chapterCount,
    lastReadAt = other.lastReadAt,
    sync = sync.copy(syncedAt = null),
)
