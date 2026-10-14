package dev.gavenda.kozeki.data.manga

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The pages of one chapter, loaded as they are needed. Nothing loads by itself: whoever reads says
 * which page is in view with [request], and the pages near it are fetched from the source a few
 * at a time, nearest first. This is the way Mihon's HttpPageLoader goes about it, kept to the
 * pages just around the one in view so that a source is never asked for a whole chapter at once.
 *
 * Asking for another page drops what was still waiting for the last one.
 */
class MangaChapterPages internal constructor(
    val refs: List<MangaPageRef>,
    private val store: MangaPages,
) {

    /** Where a page is on its way to being a file. */
    sealed interface State {
        /** Not asked for yet, or put back after being dropped. */
        data object Queue : State
        /** [progress] is how much of the page has come, from 0 to 1, or null while that cannot be told. */
        data class Loading(val progress: Float? = null) : State
        data class Ready(val file: File) : State
        data class Error(val cause: Throwable) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = refs.map { ref ->
        MutableStateFlow(store.cached(ref)?.let(State::Ready) ?: State.Queue)
    }
    private val downloads = ConcurrentHashMap<Int, Job>()
    private val chunkLock = Any()
    private var chunkJob: Job? = null

    val size: Int get() = refs.size

    fun state(index: Int): StateFlow<State> = states[index]

    /**
     * The page at [index] is the one in view. It is fetched at once, then the pages around it, the
     * next before the previous, in chunks that grow from [START_CHUNK] to [END_CHUNK] pages and
     * each finish before the next begins.
     */
    fun request(index: Int) {
        if (index !in refs.indices) return
        // The page being read goes first, before the chunks are even planned.
        launchLoadOnce(index)
        // A failed page is tried again when it comes back into view.
        val chunks = plan(index)
        val keep = chunks.take(2).flatten().toSet()
        downloads.keys.filter { it !in keep }.forEach { downloads.remove(it)?.cancel() }
        synchronized(chunkLock) {
            chunkJob?.cancel()
            chunkJob = scope.launch {
                for (chunk in chunks) {
                    chunk.mapNotNull(::launchLoadOnce).joinAll()
                }
            }
        }
    }

    /** Fetches the page at [index] again, for a button on a page that failed. */
    fun retry(index: Int) {
        if (index !in refs.indices) return
        downloads.remove(index)?.cancel()
        states[index].value = State.Queue
        request(index)
    }

    /**
     * Stops what is still being fetched without giving the chapter up, for a chapter the reader has
     * scrolled out of but may scroll back to: asking for a page again starts it.
     */
    fun idle() {
        synchronized(chunkLock) {
            chunkJob?.cancel()
            chunkJob = null
        }
        downloads.values.forEach { it.cancel() }
        downloads.clear()
    }

    /** Stops everything still being fetched. */
    fun close() {
        scope.cancel()
        downloads.clear()
    }

    /** The pages to load for [index], in the order and chunks to load them in. */
    private fun plan(index: Int): List<List<Int>> =
        rollingChunked(loadOrder(index, refs.size), START_CHUNK, END_CHUNK)

    /** The job fetching [index], started if none is and the page still needs it. Null when it has no need. */
    private fun launchLoadOnce(index: Int): Job? {
        // A page another chain is already fetching still counts towards this chunk, so a chunk
        // does not end, and the next one begin, while it is in flight.
        downloads[index]?.takeIf { it.isActive }?.let { return it }
        val state = states[index].value
        if (state !is State.Queue && state !is State.Error) return null

        val job = scope.launch(start = CoroutineStart.LAZY) { load(index) }
        val running = downloads.putIfAbsent(index, job)
        if (running != null) {
            job.cancel()
            return running
        }
        job.invokeOnCompletion { downloads.remove(index, job) }
        job.start()
        return job
    }

    private suspend fun load(index: Int) {
        val state = states[index]
        try {
            // A page already in the cache needs no source at all.
            store.cached(refs[index])?.let {
                state.value = State.Ready(it)
                return
            }
            state.value = State.Loading()
            state.value = State.Ready(store.file(refs[index]) { state.value = State.Loading(it) })
        } catch (e: CancellationException) {
            // Every page turn drops the chain before it, so a dropped page is not a failed one.
            state.value = State.Queue
            throw e
        } catch (e: Throwable) {
            state.value = State.Error(e)
        }
    }

    companion object {
        /** How many pages past the one in view are fetched ahead. */
        const val PAGES_AHEAD = 4
        const val START_CHUNK = 3
        const val END_CHUNK = 5

        /**
         * The pages worth loading when [index] of [count] is in view, nearest first: that page, the one
         * before, the one after, then on ahead, then back. Only the few just around it.
         */
        internal fun loadOrder(index: Int, count: Int): List<Int> {
            val window = (index - 1)..(index + PAGES_AHEAD)
            return buildList {
                add(index)
                if (index - 1 >= 0) add(index - 1)
                if (index + 1 < count) add(index + 1)
                for (next in index + 2 until count) add(next)
                for (previous in index - 2 downTo 0) add(previous)
            }.filter { it in window }
        }

        /** Splits [items] into chunks of [start] growing by one to [end], and [end] after that. */
        internal fun rollingChunked(items: List<Int>, start: Int, end: Int): List<List<Int>> {
            require(start >= 1) { "A chunk of $start pages never gets anywhere" }
            val chunks = ArrayList<List<Int>>()
            var size = start
            var at = 0
            while (at < items.size) {
                chunks += items.subList(at, minOf(at + size, items.size))
                at += size
                if (size < end) size++
            }
            return chunks
        }
    }
}
