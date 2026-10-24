package dev.gavenda.kozeki.data.comic

import android.content.Context
import android.util.LruCache
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.model.ComicChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import dev.gavenda.kozeki.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink

/** A page of a chapter that has been listed by its source, and can be fetched. */
class ComicPageRef(
    val sourceId: Long,
    val chapterUrl: String,
    val index: Int,
    internal val page: Page,
    /**
     * Where the page is, or is to be, as an image file: in the chapter's download, or in a folder
     * kept only while the chapter is open. Null for a page only listed.
     */
    internal val file: File? = null,
) {
    internal fun at(file: File) = ComicPageRef(sourceId, chapterUrl, index, page, file)
}

/**
 * The pages of chapters: which a chapter has, and each as an image file on this device. A page read
 * online goes into the chapter's download, when it is being downloaded. Otherwise it is a temporary
 * page, kept in a folder for its chapter until the temporary pages pass the size set in Settings,
 * when the ones read longest ago go, or until they are cleared there.
 */
class ComicPages(
    context: Context,
    private val sources: ComicSources,
    /** How many megabytes the temporary pages may take up, as the user set it. */
    limitMegabytes: Flow<Int> = flowOf(SettingsRepository.DEFAULT_TEMPORARY_PAGES_MB),
    scope: CoroutineScope? = null,
) {

    private val dir = File(context.cacheDir, "comic_reading")
    private val _temporaryBytes = MutableStateFlow(0L)

    /** How much the temporary pages take up, in bytes. */
    val temporaryBytes: StateFlow<Long> = _temporaryBytes.asStateFlow()
    private val trimLock = Any()

    @Volatile
    private var maxBytes = SettingsRepository.DEFAULT_TEMPORARY_PAGES_MB * MEGABYTE

    init {
        // Pages read online were cached in "comic_pages", and in "manga_pages" before comics were
        // called comics. Moved out of the way at once and deleted on another thread, since the old
        // cache could be large.
        listOf("comic_pages", "manga_pages").forEach { name ->
            File(context.cacheDir, name).renameTo(File(context.cacheDir, "$name.old-${System.nanoTime()}"))
        }
        val old = context.cacheDir.listFiles { file -> file.name.contains(".old-") }.orEmpty()
        thread(name = "ComicPages cleanup") {
            old.forEach { it.deleteRecursively() }
            synchronized(trimLock) { _temporaryBytes.value = temporaryFiles().sumOf { it.length() } }
        }
        // A smaller size takes effect at once.
        scope?.launch(Dispatchers.IO) {
            limitMegabytes.distinctUntilChanged().collect {
                maxBytes = it * MEGABYTE
                trim()
            }
        }
    }
    private val lists = LruCache<String, List<ComicPageRef>>(MAX_LISTS)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The pages [chapter] of [sourceId] has, in reading order. */
    suspend fun list(sourceId: Long, chapter: ComicChapter): List<ComicPageRef> =
        guardSource(site = { sources.siteOf(sourceId) }) {
        val key = "$sourceId|${chapter.url}"
        lists.get(key)?.let { return@guardSource it }
        val source = sources.get(sourceId)
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NO_EXTENSIONS)
        val stub = SChapter.create().apply {
            url = chapter.url
            name = chapter.name
            chapter_number = chapter.number ?: -1f
            scanlator = chapter.scanlator
            memo = ComicMemo.decode(chapter.memo)
        }
        val refs = source.getPageList(stub).mapIndexed { index, page ->
            ComicPageRef(sourceId, chapter.url, index, page)
        }
        if (refs.isEmpty()) throw MetadataException.Server(0, "The source lists no pages for ${chapter.url}")
        lists.put(key, refs)
        refs
    }

    /**
     * The pages of [chapter] as one thing to read: loaded a few at a time around the page in view,
     * which is for the caller to say, rather than all at once. Pages go into [into], the folder of
     * the chapter's download, and pages already there are not fetched again. Without one they are
     * temporary pages, kept in the chapter's own folder under the cache.
     */
    suspend fun open(
        sourceId: Long,
        chapter: ComicChapter,
        into: File? = null,
        onClose: () -> Unit = {},
    ): ComicChapterPages {
        val folder = into ?: temporaryFolder(sourceId, chapter.url)
        withContext(Dispatchers.IO) { folder.mkdirs() }
        val refs = list(sourceId, chapter).map { it.at(File(folder, pageName(it.index))) }
        return ComicChapterPages(refs, this, onClose = onClose)
    }

    /**
     * The pages of a downloaded chapter, [files] in reading order, as one thing to read. Nothing is
     * asked of the source.
     */
    fun openLocal(sourceId: Long, chapterUrl: String, files: List<File>): ComicChapterPages =
        ComicChapterPages(
            files.mapIndexed { index, file -> ComicPageRef(sourceId, chapterUrl, index, Page(index), file) },
            this,
        )

    /** Deletes every temporary page. Downloaded chapters stay. */
    suspend fun clearTemporary() = withContext(Dispatchers.IO) {
        synchronized(trimLock) {
            dir.deleteRecursively()
            _temporaryBytes.value = temporaryFiles().sumOf { it.length() }
        }
    }

    /** The image of [ref] if it is on this device already. */
    internal fun cached(ref: ComicPageRef): File? = ref.file?.takeIf { it.isFile && it.length() > 0 }?.also {
        // A temporary page read again is the last to be dropped.
        if (it.isTemporary()) it.setLastModified(System.currentTimeMillis())
    }

    /**
     * The image of [ref], fetched first if it is not on this device. Throws when the source refuses.
     * [onProgress] hears how much has come, from 0 to 1 in whole percents, if the source says how big
     * the image is.
     */
    internal suspend fun file(ref: ComicPageRef, onProgress: (Float) -> Unit = {}): File {
        val target = checkNotNull(ref.file) { "Page ${ref.index} of ${ref.chapterUrl} has nowhere to go" }
        fetchTo(ref, target, onProgress)
        return target
    }

    /**
     * Fetches the image of [ref] into [target], unless it is there already: the reader and a
     * download may both want the same page, and it is asked for once. Throws when the source refuses.
     */
    internal suspend fun fetchTo(ref: ComicPageRef, target: File, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            locks.getOrPut(target.path) { Mutex() }.withLock {
                if (target.isFile && target.length() > 0) return@withLock
                if (!target.isTemporary()) {
                    // A page read before without downloading is moved into the download rather than fetched again.
                    val temporary = File(temporaryFolder(ref.sourceId, ref.chapterUrl), pageName(ref.index))
                    val bytes = temporary.length()
                    if (temporary.isFile && bytes > 0 && temporary.renameTo(target)) {
                        _temporaryBytes.update { (it - bytes).coerceAtLeast(0) }
                        return@withLock
                    }
                }
                guardSource(site = { sources.siteOf(ref.sourceId) }) { download(ref, target, onProgress) }
                if (target.isTemporary()) {
                    if (_temporaryBytes.updateAndGet { it + target.length() } > maxBytes) trim()
                }
            }
        }

    private suspend fun download(ref: ComicPageRef, target: File, onProgress: (Float) -> Unit) {
        val source = sources.get(ref.sourceId) as? HttpSource
            ?: throw IOException("Comic source ${ref.sourceId} is not installed")
        val page = ref.page
        if (page.imageUrl.isNullOrEmpty()) page.imageUrl = source.getImageUrl(page)
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            source.getImage(page).use { response ->
                val body = response.body
                val total = body.contentLength()
                val source = body.source()
                temporary.sink().buffer().use { sink ->
                    var received = 0L
                    var reported = -1
                    while (true) {
                        val read = source.read(sink.buffer, CHUNK_BYTES)
                        if (read < 0) break
                        sink.emitCompleteSegments()
                        received += read
                        if (total > 0) {
                            val percent = (received * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != reported) {
                                reported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (temporary.length() == 0L) throw IOException("The source sent an empty image")
            if (!temporary.renameTo(target)) throw IOException("Could not keep the page")
        } finally {
            temporary.delete()
        }
    }

    private fun temporaryFolder(sourceId: Long, chapterUrl: String) = File(dir, hash("$sourceId|$chapterUrl"))

    private fun File.isTemporary() = path.startsWith(dir.path + File.separator)

    private fun temporaryFiles(): List<File> =
        dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()

    /**
     * Drops the temporary pages read longest ago once they are past the size allowed, down to four
     * fifths of it, so that it is not done again at the next page.
     */
    private fun trim() = synchronized(trimLock) {
        val files = temporaryFiles()
        var total = files.sumOf { it.length() }
        val max = maxBytes
        if (total > max) {
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= max * 4 / 5) break
                total -= file.length()
                file.delete()
            }
            dir.listFiles { file -> file.isDirectory && file.list().isNullOrEmpty() }.orEmpty().forEach { it.delete() }
        }
        _temporaryBytes.value = total
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_LISTS = 8
        private const val MEGABYTE = 1024L * 1024
        private const val CHUNK_BYTES = 8L * 1024

        /** The name of the file of page [index] in a chapter's folder, which sorts in reading order. */
        internal fun pageName(index: Int) = "%04d.page".format(index)
    }
}
