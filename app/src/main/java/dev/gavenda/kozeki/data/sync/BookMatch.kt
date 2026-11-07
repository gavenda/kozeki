package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.db.SyncStamp

/**
 * Whether [a] and [b] are the same book entered twice, as happens when two devices each imported
 * it before either was signed in. Book ids are made on each device, so the sync alone sees two books.
 *
 * The same EPUB settles it either way: two different files are two books, even with the same
 * details. Without a file on both sides, the same ISBN does, and failing that the same catalogue
 * entry under the same title. A title alone is not enough. With [fileOnly], only the file counts.
 */
internal fun sameBook(a: BookEntity, b: BookEntity, fileOnly: Boolean = false): Boolean = when {
    a.fileHash != null && b.fileHash != null -> a.fileHash == b.fileHash
    fileOnly -> false
    a.isbn13 != null && b.isbn13 != null -> a.isbn13 == b.isbn13
    a.isbn10 != null && b.isbn10 != null -> a.isbn10 == b.isbn10
    a.source != null && a.sourceId != null -> a.source == b.source && a.sourceId == b.sourceId &&
        a.title.trim().equals(b.title.trim(), ignoreCase = true)
    else -> false
}

/**
 * [kept] and [dropped], two entries of one book, as one under [kept]'s id. The details come from
 * whichever was edited later and the reading position from whichever was read later, as the sync
 * decides between two versions of a row. What only the other had fills in what is missing: its
 * file, a cover, a rating, a purchase. The result is stamped [now], so it goes out as the newest.
 */
internal fun combineBooks(kept: BookEntity, dropped: BookEntity, now: Long): BookEntity {
    val details = if (dropped.sync.updatedAt > kept.sync.updatedAt) dropped else kept
    val other = if (details === kept) dropped else kept
    val reader = if ((dropped.lastReadAt ?: 0L) > (kept.lastReadAt ?: 0L)) dropped else kept
    val file = if (kept.fileHash != null) kept else dropped
    val cover = when {
        details.customCover -> details
        other.customCover -> other
        details.coverFile != null -> details
        else -> other
    }
    val purchase = if (details.purchasedOn != null || details.purchasePriceMinor != null) details else other
    return details.copy(
        id = kept.id,
        fileHash = file.fileHash,
        fileSize = file.fileSize,
        originalFileName = file.originalFileName,
        coverFile = cover.coverFile,
        customCover = cover.customCover,
        // A book read on one side is not put back to Planned by the other.
        state = if (details.state == ReadingState.PLANNED) other.state else details.state,
        acquisition = if (Acquisition.PURCHASED in listOf(details.acquisition, other.acquisition)) {
            Acquisition.PURCHASED
        } else {
            details.acquisition
        },
        isFavorite = kept.isFavorite || dropped.isFavorite,
        rating = details.rating ?: other.rating,
        purchasePriceMinor = purchase.purchasePriceMinor,
        purchaseCurrency = purchase.purchaseCurrency,
        purchasedOn = purchase.purchasedOn,
        purchaseLocation = purchase.purchaseLocation ?: other.purchaseLocation,
        purchaseMemo = purchase.purchaseMemo ?: other.purchaseMemo,
        physicalPage = details.physicalPage ?: other.physicalPage,
        physicalPageCount = details.physicalPageCount ?: other.physicalPageCount,
        locator = reader.locator,
        progression = reader.progression,
        position = reader.position,
        positionCount = reader.positionCount,
        chapterTitle = reader.chapterTitle,
        chapterIndex = reader.chapterIndex,
        chapterCount = reader.chapterCount,
        lastReadAt = reader.lastReadAt,
        sync = SyncStamp(
            createdAt = minOf(kept.sync.createdAt, dropped.sync.createdAt),
            updatedAt = now,
            syncedAt = null,
        ),
    )
}

/**
 * Pairs each of [books] with the earlier entry it repeats, as (dropped, kept). The earliest
 * entry of a book is the one kept, so two devices doing this apart come to the same answer.
 */
internal fun duplicatePairs(books: List<BookEntity>, fileOnly: Boolean): List<Pair<BookEntity, BookEntity>> {
    val kept = mutableListOf<BookEntity>()
    val pairs = mutableListOf<Pair<BookEntity, BookEntity>>()
    books.sortedWith(compareBy({ it.sync.createdAt }, { it.id })).forEach { book ->
        val first = kept.firstOrNull { sameBook(it, book, fileOnly) }
        if (first == null) kept += book else pairs += book to first
    }
    return pairs
}

/**
 * Pairs each of [device]'s books with the [account] book it repeats, as (dropped, kept). The
 * account's entry is kept, since other devices already have it; each is matched once.
 */
internal fun accountPairs(device: List<BookEntity>, account: List<BookEntity>): List<Pair<BookEntity, BookEntity>> {
    val free = account.toMutableList()
    return device.sortedWith(compareBy({ it.sync.createdAt }, { it.id })).mapNotNull { book ->
        val match = free.firstOrNull { sameBook(it, book) } ?: return@mapNotNull null
        free -= match
        book to match
    }
}
