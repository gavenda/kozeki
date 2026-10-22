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

/**
 * Chapters of comics kept on this device to be read without their source, as Mihon keeps them.
 * They are fetched one chapter at a time and one page at a time, in the order they were asked for,
 * and live under the app's own files, apart from the cache of pages read online, so nothing drops
 * them but the user. They are no part of a backup.
 *
 * A chapter is a folder of numbered page files under its book's folder, named after the chapter's
 * URL, with the URL itself written beside the pages. It only takes that name once every page is in.
 * The queue is written down too, so a download the app was closed in the middle of goes on the next
 * time it starts.
 */
class ComicDownloads(
    context: Context,
    private val db: KozekiDatabase,
    private val pages: ComicPages,
    private val scope: CoroutineScope,
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

    /** How many chapters of each book are downloaded, for the books with any. */
    val counts: Flow<Map<String, Int>> =
        _downloaded.map { map -> map.mapValues { it.value.size }.filterValues { it > 0 } }.distinctUntilChanged()

    /** Puts [chapters] of [bookId] in the queue, in the order given, but for those kept or queued already. */
    fun enqueue(bookId: String, chapters: List<ComicChapter>) {
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
                    retried + asked.filter { it.url !in kept && it.url !in queued }.map { Task(bookId, it, ChapterDownload.Queued) }
                }
                saveQueue()
            }
            start()
        }
    }

    /** Takes [urls] of [bookId] out of the queue, stopping the one being fetched, if it is one of them. */
    fun cancel(bookId: String, urls: Collection<String>) {
        scope.launch {
            lock.withLock {
                queue.update { tasks -> tasks.filterNot { it.bookId == bookId && it.chapter.url in urls } }
                saveQueue()
            }
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

        val partial = File(bookDir(bookId), hash(chapter.url) + PARTIAL_SUFFIX)
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
                val target = File(partial, "%04d%s".format(ref.index, PAGE_SUFFIX))
                if (!(target.isFile && target.length() > 0)) pages.fetchTo(ref, target)
                set(ChapterDownload.Downloading(ref.index + 1, refs.size))
            }
            File(partial, URL_FILE).writeText(chapter.url)
            val done = File(bookDir(bookId), hash(chapter.url))
            done.deleteRecursively()
            if (!partial.renameTo(done)) throw IllegalStateException("Could not keep the chapter")
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
            book.name to book.listFiles { dir -> dir.isDirectory && !dir.name.endsWith(PARTIAL_SUFFIX) }.orEmpty()
                .mapNotNullTo(HashSet()) { dir -> runCatching { File(dir, URL_FILE).readText() }.getOrNull() }
        }.filterValues { it.isNotEmpty() }
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
        const val PARTIAL_SUFFIX = ".part"
        const val URL_FILE = "url"
    }
}
