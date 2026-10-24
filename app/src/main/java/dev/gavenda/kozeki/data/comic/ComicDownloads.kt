package dev.gavenda.kozeki.data.comic

import android.content.Context
import android.util.Log
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.recognizeChapterNumber
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a chapter of a comic is on its way to being kept on this device. */
sealed interface ChapterDownload {
    /** Waiting for the chapters before it in the queue. */
    data object Queued : ChapterDownload

    /** [done] of [total] pages are in; [total] is null until the source has listed them. */
    data class Downloading(val done: Int = 0, val total: Int? = null) : ChapterDownload {
        val progress: Float? get() = total?.takeIf { it > 0 }?.let { done.toFloat() / it }
    }

    /** It stopped; it stays in the queue until it is tried again or taken out. */
    data class Failed(val cause: Throwable) : ChapterDownload

    /** Every page is on this device. */
    data object Downloaded : ChapterDownload
}

/** A chapter in the download queue, waiting, coming in or stopped. */
data class QueuedDownload(val bookId: String, val chapter: ComicChapter, val state: ChapterDownload)

/**
 * Chapters of comics kept on this device to be read without their source, as Mihon keeps them.
 * They are fetched one chapter at a time and one page at a time, in the order they were asked for,
 * and live under the app's own files, so nothing drops them but the user. They are no part of a
 * backup. The reader puts the pages it fetches into the same folder, so a chapter read while it is
 * downloading has none of its pages asked for twice.
 *
 * A chapter is a folder of numbered page files under its book's folder, named after the chapter's
 * URL. The URL itself is written beside the pages once every page is in, which is what marks the
 * chapter downloaded. The queue is written down too, so a download the app was closed in the middle
 * of goes on the next time it starts.
 */
class ComicDownloads(
    context: Context,
    private val db: KozekiDatabase,
    private val pages: ComicPages,
    private val scope: CoroutineScope,
    /** Called as the queue starts being worked through, to keep the app running until it is done. */
    private val keepAlive: () -> Unit = {},
) {

    private val root = File(context.filesDir, "comic_downloads")
    private val queueFile = File(root, "queue.json")
    private val json = Json { ignoreUnknownKeys = true }

    /** One chapter waiting or being fetched. */
    private data class Task(val bookId: String, val chapter: ComicChapter, val state: ChapterDownload)

    @Serializable
    private data class QueuedChapter(val bookId: String, val url: String)

    private val _downloaded = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** The URLs of the chapters kept on this device, by the ID of their book. Empty until looked for. */
    val downloaded: StateFlow<Map<String, Set<String>>> = _downloaded.asStateFlow()

    private val queue = MutableStateFlow<List<Task>>(emptyList())
    private val lock = Mutex()
    private var runner: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            renamePartials()
            _downloaded.value = scan()
            restoreQueue()
            // A book removed from the library takes its chapters with it.
            db.bookDao().observeAll().map { rows -> rows.mapTo(HashSet()) { it.id } }.distinctUntilChanged()
                .collect { alive -> dropOrphans(alive) }
        }
    }

    /** How each chapter of [bookId] that is downloaded, or on its way, stands, by URL. */
    fun observe(bookId: String): Flow<Map<String, ChapterDownload>> =
        combine(_downloaded, queue) { done, tasks ->
            buildMap {
                done[bookId]?.forEach { put(it, ChapterDownload.Downloaded) }
                tasks.forEach { if (it.bookId == bookId) put(it.chapter.url, it.state) }
            }
        }.distinctUntilChanged()

    /** Every chapter in the queue, in the order it will be fetched. */
    val queued: Flow<List<QueuedDownload>> =
        queue.map { tasks -> tasks.map { QueuedDownload(it.bookId, it.chapter, it.state) } }.distinctUntilChanged()

    /** How many chapters of each book are downloaded, for the books with any. */
    val counts: Flow<Map<String, Int>> =
        _downloaded.map { map -> map.mapValues { it.value.size }.filterValues { it > 0 } }.distinctUntilChanged()

    /**
     * Puts [chapters] of [bookId] in the queue, in the order given, but for those kept or queued
     * already. [first] puts them next in line, straight after the chapter coming in now, as for a
     * chapter being read; one queued already is moved there too.
     */
    fun enqueue(bookId: String, chapters: List<ComicChapter>, first: Boolean = false) {
        scope.launch {
            lock.withLock {
                val kept = _downloaded.value[bookId].orEmpty()
                val asked = chapters.distinctBy { it.url }
                val urls = asked.mapTo(HashSet()) { it.url }
                queue.update { tasks ->
                    // A chapter that failed and is asked for again goes back to waiting.
                    val retried = tasks.map { task ->
                        val again = task.bookId == bookId && task.chapter.url in urls && task.state is ChapterDownload.Failed
                        if (again) task.copy(state = ChapterDownload.Queued) else task
                    }
                    val queued = tasks.filter { it.bookId == bookId }.mapTo(HashSet()) { it.chapter.url }
                    val added = asked.filter { it.url !in kept && it.url !in queued }.map { Task(bookId, it, ChapterDownload.Queued) }
                    if (!first) return@update retried + added
                    val (moved, rest) = retried.partition {
                        it.bookId == bookId && it.chapter.url in urls && it.state !is ChapterDownload.Downloading
                    }
                    val at = rest.indexOfFirst { it.state is ChapterDownload.Downloading } + 1
                    rest.take(at) + moved + added + rest.drop(at)
                }
                saveQueue()
            }
            start()
        }
    }

    /**
     * Takes [urls] of [bookId] out of the queue, stopping the one being fetched, if it is one of
     * them. The pages that had come are deleted.
     */
    fun cancel(bookId: String, urls: Collection<String>) {
        scope.launch {
            val dropped = lock.withLock {
                val dropped = queue.value.filter { it.bookId == bookId && it.chapter.url in urls }
                queue.update { tasks -> tasks - dropped.toSet() }
                saveQueue()
                dropped
            }
            deleteUnfinished(dropped)
        }
    }

    /**
     * Moves the chapter [url] of [bookId] to the front of the queue, to be fetched next. The one
     * coming in now is not stopped for it.
     */
    fun moveToFront(bookId: String, url: String) {
        scope.launch {
            lock.withLock {
                queue.update { tasks ->
                    val task = tasks.firstOrNull { it.bookId == bookId && it.chapter.url == url } ?: return@update tasks
                    val rest = tasks - task
                    // Straight after the one coming in, which the queue shows first.
                    val at = rest.indexOfFirst { it.state is ChapterDownload.Downloading } + 1
                    rest.take(at) + task + rest.drop(at)
                }
                saveQueue()
            }
        }
    }

    /** Puts every chapter that stopped back to waiting. */
    fun retryFailed() {
        scope.launch {
            lock.withLock {
                queue.update { tasks ->
                    tasks.map { if (it.state is ChapterDownload.Failed) it.copy(state = ChapterDownload.Queued) else it }
                }
            }
            start()
        }
    }

    /** Empties the queue, stopping the chapter coming in. What is downloaded already stays. */
    fun cancelAll() {
        scope.launch {
            val dropped = lock.withLock {
                val dropped = queue.value
                queue.value = emptyList()
                saveQueue()
                dropped
            }
            deleteUnfinished(dropped)
        }
    }

    /** Whether the chapter [url] of [bookId] is in the queue, waiting, coming in or stopped. */
    fun isQueued(bookId: String, url: String): Boolean =
        queue.value.any { it.bookId == bookId && it.chapter.url == url }

    /**
     * The folder the pages of the chapter [url] of [bookId] go in, for the reader to put the pages
     * it fetches in while the chapter is downloading. It is made if it is not there.
     */
    fun folder(bookId: String, url: String): File = File(bookDir(bookId), hash(url)).apply { mkdirs() }

    /**
     * The reader is done with the chapter [url] of [bookId], whose pages it put in its [folder]:
     * they go unless the chapter is downloaded or in the queue.
     */
    fun release(bookId: String, url: String) {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                if (isQueued(bookId, url) || url in _downloaded.value[bookId].orEmpty()) return@withLock
                File(bookDir(bookId), hash(url)).deleteRecursively()
            }
        }
    }

    /** Deletes what had come of [tasks], which have been taken out of the queue. */
    private suspend fun deleteUnfinished(tasks: List<Task>) = withContext(Dispatchers.IO) {
        for (task in tasks) {
            if (task.chapter.url in _downloaded.value[task.bookId].orEmpty()) continue
            File(bookDir(task.bookId), hash(task.chapter.url)).deleteRecursively()
        }
    }

    /** Deletes the downloaded [urls] of [bookId], and takes any of them still on their way out of the queue. */
    fun delete(bookId: String, urls: Collection<String>) {
        cancel(bookId, urls)
        scope.launch(Dispatchers.IO) {
            val bookDir = bookDir(bookId)
            urls.forEach { File(bookDir, hash(it)).deleteRecursively() }
            if (bookDir.list()?.isEmpty() == true) bookDir.delete()
            _downloaded.update { map ->
                val left = map[bookId].orEmpty() - urls.toSet()
                if (left.isEmpty()) map - bookId else map + (bookId to left)
            }
        }
    }

    /** The page files of a downloaded chapter in reading order, or null when it is not downloaded. */
    suspend fun files(bookId: String, url: String): List<File>? = withContext(Dispatchers.IO) {
        if (url !in _downloaded.value[bookId].orEmpty()) return@withContext null
        val dir = File(bookDir(bookId), hash(url))
        dir.listFiles { file -> file.name.endsWith(PAGE_SUFFIX) }?.sortedBy { it.name }?.takeIf { it.isNotEmpty() }
    }

    private fun start() {
        if (runner?.isActive == true) return
        keepAlive()
        runner = scope.launch(Dispatchers.IO) {
            while (true) {
                val task = lock.withLock { queue.value.firstOrNull { it.state is ChapterDownload.Queued } } ?: break
                download(task)
            }
        }
    }

    private suspend fun download(task: Task) {
        val (bookId, chapter) = task.bookId to task.chapter
        fun set(state: ChapterDownload) = queue.update { tasks ->
            tasks.map { if (it.bookId == bookId && it.chapter.url == chapter.url) it.copy(state = state) else it }
        }
        fun stillQueued() = queue.value.any { it.bookId == bookId && it.chapter.url == chapter.url }

        val partial = File(bookDir(bookId), hash(chapter.url))
        try {
            set(ChapterDownload.Downloading())
            val book = db.bookDao().get(bookId) ?: throw IllegalStateException("The book is gone")
            val sourceId = book.sourceId?.let { ComicProvider.mihonSourceOf(it) }
                ?: throw IllegalStateException("Not a comic: ${book.sourceId}")
            val refs = pages.list(sourceId, chapter)
            partial.mkdirs()
            set(ChapterDownload.Downloading(0, refs.size))
            for (ref in refs) {
                // Taken out of the queue while it was being fetched.
                if (!stillQueued()) {
                    partial.deleteRecursively()
                    return
                }
                // A page the reader fetched already is there, and not asked for again.
                pages.fetchTo(ref, File(partial, ComicPages.pageName(ref.index)))
                set(ChapterDownload.Downloading(ref.index + 1, refs.size))
            }
            // Written last, as the mark that every page is in.
            File(partial, URL_FILE).writeText(chapter.url)
            lock.withLock {
                _downloaded.update { it + (bookId to (it[bookId].orEmpty() + chapter.url)) }
                queue.update { tasks -> tasks.filterNot { it.bookId == bookId && it.chapter.url == chapter.url } }
                saveQueue()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Could not download ${chapter.url}", e)
            // What came is kept, so that trying again starts where it stopped.
            set(ChapterDownload.Failed(e))
        }
    }

    private fun bookDir(bookId: String) = File(root, bookId)

    /** What is on this device, read from the folders. Half-done chapters are left for their download to finish. */
    private fun scan(): Map<String, Set<String>> {
        val books = root.listFiles { file -> file.isDirectory } ?: return emptyMap()
        return books.associate { book ->
            book.name to book.listFiles { dir -> dir.isDirectory }.orEmpty()
                .mapNotNullTo(HashSet()) { dir -> runCatching { File(dir, URL_FILE).readText() }.getOrNull() }
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * A chapter on its way down was once in a folder ending in [LEGACY_PARTIAL_SUFFIX], renamed
     * when it was done. It takes its own name now, so that its download goes on in it.
     */
    private fun renamePartials() {
        root.listFiles { file -> file.isDirectory }.orEmpty().forEach { book ->
            book.listFiles { dir -> dir.isDirectory && dir.name.endsWith(LEGACY_PARTIAL_SUFFIX) }.orEmpty().forEach { part ->
                val named = File(book, part.name.removeSuffix(LEGACY_PARTIAL_SUFFIX))
                if (named.exists() || !part.renameTo(named)) part.deleteRecursively()
            }
        }
    }

    private suspend fun dropOrphans(alive: Set<String>) {
        val gone = root.listFiles { file -> file.isDirectory && file.name !in alive }.orEmpty()
        gone.forEach { it.deleteRecursively() }
        val ids = gone.mapTo(HashSet()) { it.name }
        lock.withLock {
            if (ids.isNotEmpty()) _downloaded.update { it - ids }
            if (queue.value.any { it.bookId !in alive }) {
                queue.update { tasks -> tasks.filter { it.bookId in alive } }
                saveQueue()
            }
        }
    }

    private suspend fun restoreQueue() {
        val saved = runCatching {
            json.decodeFromString<List<QueuedChapter>>(queueFile.readText())
        }.getOrNull().orEmpty()
        if (saved.isEmpty()) return
        val chapters = db.comicChapterDao()
        val tasks = saved.mapNotNull { item ->
            val row = chapters.get(item.bookId, item.url) ?: return@mapNotNull null
            Task(
                bookId = item.bookId,
                chapter = ComicChapter(
                    url = row.url,
                    name = row.name,
                    number = row.chapterNumber.takeIf { it >= 0f } ?: recognizeChapterNumber(row.name),
                    scanlator = row.scanlator,
                    uploadedAt = row.uploadedAt.takeIf { it > 0 },
                    sourceOrder = row.sourceOrder,
                    read = row.read,
                    lastPageRead = row.lastPageRead,
                    pageCount = row.pageCount,
                    memo = row.memo,
                ),
                state = ChapterDownload.Queued,
            )
        }
        lock.withLock { queue.update { tasks + it } }
        start()
    }

    /** Called with [lock] held. */
    private fun saveQueue() {
        runCatching {
            root.mkdirs()
            val items = queue.value.map { QueuedChapter(it.bookId, it.chapter.url) }
            if (items.isEmpty()) queueFile.delete() else queueFile.writeText(json.encodeToString(items))
        }.onFailure { Log.w(TAG, "Could not write the download queue down", it) }
    }

    private fun hash(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "ComicDownloads"
        const val PAGE_SUFFIX = ".page"
        const val LEGACY_PARTIAL_SUFFIX = ".part"
        const val URL_FILE = "url"
    }
}
