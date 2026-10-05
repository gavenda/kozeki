package dev.gavenda.kozeki.data.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns where book files live inside app-private storage. Nothing outside this class should build
 * paths by hand.
 *
 * ```
 * files/books/<bookId>.epub     the imported EPUB, excluded from cloud backup
 * files/covers/<name>.jpg       cover images, small enough to back up
 * cache/import/<uuid>.epub      a file being imported, before it is known to be valid
 * ```
 */
class BookStorage(context: Context) {

    private val booksDir = File(context.filesDir, "books")
    private val coversDir = File(context.filesDir, "covers")
    private val importDir = File(context.cacheDir, "import")
    private val resolver: ContentResolver = context.contentResolver

    fun epubFile(bookId: String): File = File(booksDir, "$bookId.epub")

    fun hasEpub(bookId: String): Boolean = epubFile(bookId).isFile

    fun coverFile(name: String): File = File(coversDir, name)

    /** Copies the picked document into the cache, hashing it on the way. */
    suspend fun copyToStaging(uri: Uri): StagedFile = withContext(Dispatchers.IO) {
        importDir.mkdirs()
        val target = File(importDir, "${UUID.randomUUID()}.epub")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            input.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        sink.write(buffer, 0, read)
                        size += read
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        StagedFile(
            file = target,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            size = size,
            displayName = displayName(uri),
        )
    }

    /** Moves a staged file into place as the EPUB of [bookId]. */
    suspend fun adopt(staged: StagedFile, bookId: String): Unit = withContext(Dispatchers.IO) {
        booksDir.mkdirs()
        val target = epubFile(bookId)
        if (!staged.file.renameTo(target)) {
            staged.file.copyTo(target, overwrite = true)
            staged.file.delete()
        }
    }

    /** Saves [bitmap] as the cover of [bookId] and returns the file name to store on the book. */
    suspend fun saveCover(bookId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        coversDir.mkdirs()
        val name = coverName(bookId)
        coverFile(name).outputStream().use { bitmap.scaledToFit().compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }

    /** Saves already-encoded image bytes, for covers downloaded from a metadata source. */
    suspend fun saveCover(bookId: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        coversDir.mkdirs()
        val name = coverName(bookId)
        coverFile(name).writeBytes(bytes)
        name
    }

    /**
     * Saves the picture behind [uri], one the user picked, as the cover of [bookId]. Returns null
     * when it cannot be read as an image.
     */
    suspend fun saveCover(bookId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                // Photos are far larger than a cover is ever shown, so they are shrunk while decoding.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_COVER_EDGE) {
                    val scale = MAX_COVER_EDGE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull() ?: return@withContext null
        saveCover(bookId, bitmap)
    }

    suspend fun deleteCover(name: String?): Unit = withContext(Dispatchers.IO) {
        if (name != null) coverFile(name).delete()
    }

    suspend fun deleteBookFiles(bookId: String, coverName: String?): Unit = withContext(Dispatchers.IO) {
        epubFile(bookId).delete()
        if (coverName != null) coverFile(coverName).delete()
    }

    // A fresh name per save, so image caches keyed by path never show a stale cover.
    private fun coverName(bookId: String): String = "$bookId-${System.currentTimeMillis()}.jpg"

    private fun Bitmap.scaledToFit(): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= MAX_COVER_EDGE) return this
        val scale = MAX_COVER_EDGE.toFloat() / longest
        return scale((width * scale).toInt(), (height * scale).toInt())
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    data class StagedFile(
        val file: File,
        val sha256: String,
        val size: Long,
        val displayName: String?,
    )

    private companion object {
        const val MAX_COVER_EDGE = 1200
    }
}
