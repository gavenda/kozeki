package dev.gavenda.kozeki.data.comic

/**
 * How often a comic comes out, read from the days its chapters were put up, so a check for new
 * chapters can leave alone a comic whose next one is not due yet.
 *
 * Chapters put up on the same day count as one release, so a batch uploaded at once, or the whole
 * back catalogue dated the day it was added, does not make a comic look daily.
 */
internal object ReleaseSchedule {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Releases looked at, the latest ones: a comic that changed pace is judged by its new pace. */
    private const val RECENT_RELEASES = 10

    /** Fewer releases than this say nothing about a pace, and the comic is always asked. */
    private const val MIN_RELEASES = 3

    private const val MIN_INTERVAL_MS = DAY_MS
    private const val MAX_INTERVAL_MS = 28 * DAY_MS

    /** Release days are rough, and a chapter a day early is still worth finding. */
    const val GRACE_MS = DAY_MS

    /**
     * The usual time between releases, from the upload times [uploads] in epoch milliseconds (0 or
     * less for unknown), or null when there are too few to tell. The median gap, so one long break
     * or one double week does not set the pace. Kept between a day and four weeks.
     */
    fun interval(uploads: List<Long>): Long? {
        val days = uploads.asSequence()
            .filter { it > 0 }
            .map { it / DAY_MS }
            .distinct()
            .sortedDescending()
            .take(RECENT_RELEASES)
            .toList()
        if (days.size < MIN_RELEASES) return null
        val gaps = days.zipWithNext { later, earlier -> (later - earlier) * DAY_MS }.sorted()
        val middle = gaps.size / 2
        val median = if (gaps.size % 2 == 1) gaps[middle] else (gaps[middle - 1] + gaps[middle]) / 2
        return median.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    /**
     * Whether a comic whose chapters were put up at [uploads] is worth asking about at [now], having
     * last been asked at [checkedAt] (0 for never).
     *
     * Releases are expected every interval after the latest one. The comic is due once the next is
     * close, and stays due until asked; after that it waits for the next expected release, so a late
     * chapter is looked for once a cycle rather than on every run. A comic never asked, or without a
     * pace to go by, is always due.
     */
    fun isDue(uploads: List<Long>, checkedAt: Long, now: Long): Boolean {
        if (checkedAt <= 0) return true
        val interval = interval(uploads) ?: return true
        val latest = uploads.max()
        // The latest expected release that is already close: the n-th interval after the latest one.
        val cycles = (now + GRACE_MS - latest).floorDiv(interval)
        if (cycles < 1) return false
        val expected = latest + cycles * interval
        return checkedAt < expected - GRACE_MS
    }
}
