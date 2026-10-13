package dev.gavenda.kozeki.data.manga

import android.content.Context
import android.util.LruCache
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.model.MangaChapter
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
class MangaPageRef(
    val sourceId: Long,
    val chapterUrl: String,
    val index: Int,
    internal val page: Page,
) {
    /** Names the page in the cache, so the same page is never fetched twice. */
    internal val key: String get() = "$sourceId|$chapterUrl|$index"
}

/**
 * The pages of chapters: which a chapter has, and each as an image file on this device. A page is
 * fetched through its source once, kept in a cache that holds the most recent few hundred
 * megabytes, and read from there afterwards, offline included.
 */
class MangaPages(context: Context, private val sources: MangaSources) {

    private val dir = File(context.cacheDir, "manga_pages").apply { mkdirs() }
    private val lists = LruCache<String, List<MangaPageRef>>(MAX_LISTS)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The pages [chapter] of [sourceId] has, in reading order. */
    suspend fun list(sourceId: Long, chapter: MangaChapter): List<MangaPageRef> = guardSource {
        val key = "$sourceId|${chapter.url}"
        lists.get(key)?.let { return@guardSource it }
        val source = sources.get(sourceId)
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NO_EXTENSIONS)
        val stub = SChapter.create().apply {
            url = chapter.url
            name = chapter.name
            chapter_number = chapter.number ?: -1f
            scanlator = chapter.scanlator
            memo = MangaMemo.decode(chapter.memo)
        }
        val refs = source.getPageList(stub).mapIndexed { index, page ->
            MangaPageRef(sourceId, chapter.url, index, page)
        }
        if (refs.isEmpty()) throw MetadataException.Server(0, "The source lists no pages for ${chapter.url}")
        lists.put(key, refs)
        refs
    }

    /**
     * The pages of [chapter] as one thing to read: loaded a few at a time around the page in view,
     * which is for the caller to say, rather than all at once.
     */
    suspend fun open(sourceId: Long, chapter: MangaChapter): MangaChapterPages =
        MangaChapterPages(list(sourceId, chapter), this)

    /** The image of [ref] if it is in the cache already. */
    internal fun cached(ref: MangaPageRef): File? =
        File(dir, hash(ref.key)).takeIf { it.isFile && it.length() > 0 }

    /** The image of [ref], fetched first if it is not in the cache. Throws when the source refuses. */
    internal suspend fun file(ref: MangaPageRef): File = withContext(Dispatchers.IO) {
        cached(ref)?.let {
            // Read recently, so it is the last to be dropped.
            it.setLastModified(System.currentTimeMillis())
            return@withContext it
        }
        val target = File(dir, hash(ref.key))
        locks.getOrPut(ref.key) { Mutex() }.withLock {
            if (target.isFile && target.length() > 0) return@withLock target
            download(ref, target)
            trim()
            target
        }
    }

    private suspend fun download(ref: MangaPageRef, target: File) {
        val source = sources.get(ref.sourceId) as? HttpSource
            ?: throw IOException("Manga source ${ref.sourceId} is not installed")
        val page = ref.page
        if (page.imageUrl.isNullOrEmpty()) page.imageUrl = source.getImageUrl(page)
        val temporary = File(dir, target.name + ".tmp")
        try {
            source.getImage(page).use { response ->
                temporary.sink().buffer().use { sink -> sink.writeAll(response.body.source()) }
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
        const val MAX_BYTES = 250L * 1024 * 1024
        const val KEEP_BYTES = 200L * 1024 * 1024
    }
}
