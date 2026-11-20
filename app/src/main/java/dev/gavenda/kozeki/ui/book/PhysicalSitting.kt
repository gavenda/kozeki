package dev.gavenda.kozeki.ui.book

/**
 * The clock of a sitting in the reading mode. It runs from when the mode opens until it closes,
 * except while paused or while the app is out of sight, and hands its time over in [Part]s to be
 * kept as sessions: when the mode closes, and when the app goes out of sight, so that time is not
 * lost if the app is never come back to.
 */
class PhysicalSitting(private val now: () -> Long) {

    /** Time to keep as one session, from [startedAt] to [endedAt], of which [durationMs] was read. */
    data class Part(val startedAt: Long, val endedAt: Long, val durationMs: Long)

    /** What the timer shows: [baseMs] plus the time since [runningSince] while it runs. */
    data class Clock(val baseMs: Long, val runningSince: Long?)

    private var open = false
    private var partStartedAt = 0L
    private var keptMs = 0L
    private var pendingMs = 0L
    private var runningSince: Long? = null
    private var wasRunning = false

    val isOpen: Boolean get() = open
    val isRunning: Boolean get() = runningSince != null
    val clock: Clock get() = Clock(keptMs + pendingMs, runningSince)

    fun start() {
        if (open) return
        open = true
        keptMs = 0L
        pendingMs = 0L
        partStartedAt = now()
        runningSince = partStartedAt
    }

    fun pause() {
        val since = runningSince ?: return
        pendingMs += (now() - since).coerceAtLeast(0L)
        runningSince = null
    }

    fun resume() {
        if (!open || runningSince != null) return
        runningSince = now()
        if (pendingMs == 0L) partStartedAt = runningSince!!
    }

    /** The app went out of sight: the clock stops, and what it has is handed over. */
    fun hide(): Part? {
        if (!open) return null
        wasRunning = isRunning
        pause()
        return take(keepShort = true)
    }

    /** The app came back: the clock runs again if it was running when it went. */
    fun show() {
        if (open && wasRunning) resume()
        wasRunning = false
    }

    fun close(): Part? {
        if (!open) return null
        pause()
        val part = take(keepShort = false)
        open = false
        return part
    }

    /**
     * The time not yet handed over, as a part. Too little to be worth a session is kept for the
     * next part when [keepShort], else dropped.
     */
    private fun take(keepShort: Boolean): Part? {
        val duration = pendingMs + (runningSince?.let { now() - it } ?: 0L)
        if (duration < MIN_PART_MS) {
            if (!keepShort) pendingMs = 0L
            return null
        }
        val end = now()
        val part = Part(partStartedAt, end, duration)
        keptMs += duration
        pendingMs = 0L
        if (runningSince != null) runningSince = end
        partStartedAt = end
        return part
    }

    companion object {
        /** As short as a session in the reader may be. */
        const val MIN_PART_MS = 15_000L
    }
}
