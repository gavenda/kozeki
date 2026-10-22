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
import kotlinx.coroutines.Dispatchers
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
    /** The page as a downloaded file, for a chapter kept on this device; null for one read from its source. */
    internal val local: File? = null,
) {
    /** Names the page in the cache, so the same page is never fetched twice. */
    internal val key: String get() = "$sourceId|$chapterUrl|$index"
}

/**
 * The pages of chapters: which a chapter has, and each as an image file on this device. A page is
 * fetched through its source once, kept in a cache that holds the most recent few hundred
 * megabytes, and read from there afterwards, offline included.
 */
class ComicPages(context: Context, private val sources: ComicSources) {

    private val dir = File(context.cacheDir, "comic_pages").apply { mkdirs() }

    init {
        // Pages were cached in "manga_pages" before comics were called comics. They are fetched again when needed.
        File(context.cacheDir, "manga_pages").takeIf { it.exists() }?.deleteRecursively()
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
     * which is for the caller to say, rather than all at once.
     */
    suspend fun open(sourceId: Long, chapter: ComicChapter): ComicChapterPages =
        ComicChapterPages(list(sourceId, chapter), this)

    /**
     * The pages of a downloaded chapter, [files] in reading order, as one thing to read. Nothing is
     * asked of the source.
     */
    fun openLocal(sourceId: Long, chapterUrl: String, files: List<File>): ComicChapterPages =
        ComicChapterPages(
            files.mapIndexed { index, file -> ComicPageRef(sourceId, chapterUrl, index, Page(index), local = file) },
            this,
        )

    /** The image of [ref] if it is downloaded or in the cache already. */
    internal fun cached(ref: ComicPageRef): File? =
        ref.local?.takeIf { it.isFile && it.length() > 0 }
            ?: File(dir, hash(ref.key)).takeIf { it.isFile && it.length() > 0 }

    /**
     * The image of [ref], fetched first if it is not in the cache. Throws when the source refuses.
     * [onProgress] hears how much has come, from 0 to 1 in whole percents, if the source says how big
     * the image is.
     */
    internal suspend fun file(ref: ComicPageRef, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        cached(ref)?.let {
            // Read recently, so it is the last to be dropped.
            it.setLastModified(System.currentTimeMillis())
            return@withContext it
        }
        val target = File(dir, hash(ref.key))
        locks.getOrPut(ref.key) { Mutex() }.withLock {
            if (target.isFile && target.length() > 0) return@withLock target
            guardSource(site = { sources.siteOf(ref.sourceId) }) { download(ref, target, onProgress) }
            trim()
            target
        }
    }

    /**
     * Fetches the image of [ref] into [target], for a chapter being downloaded. A page already in
     * the cache is copied from there rather than asked for again. Throws when the source refuses.
     */
    internal suspend fun fetchTo(ref: ComicPageRef, target: File) = withContext(Dispatchers.IO) {
        cached(ref)?.let {
            it.copyTo(target, overwrite = true)
            return@withContext
        }
        guardSource(site = { sources.siteOf(ref.sourceId) }) { download(ref, target) {} }
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

    /** Drops the pages read longest ago once the cache is past its size. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= KEEP_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_LISTS = 8
        const val CHUNK_BYTES = 8L * 1024
        const val MAX_BYTES = 250L * 1024 * 1024
        const val KEEP_BYTES = 200L * 1024 * 1024
    }
}
