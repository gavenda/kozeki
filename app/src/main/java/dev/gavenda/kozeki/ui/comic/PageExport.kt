package dev.gavenda.kozeki.ui.comic

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.gavenda.kozeki.data.comic.PageImages
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A page of a comic, saved to the device's pictures or handed to another app. */
internal object PageExport {

    /** The page in a form other apps can open, and how to write it. */
    private class Image(val mime: String, val extension: String, val write: (OutputStream) -> Unit)

    /**
     * AVIF and JPEG XL are written out as PNG, since few apps open them. Anything else the platform
     * decodes is passed on as it is.
     */
    private fun image(file: File): Image? {
        PageImages.sniff(file)?.let { format ->
            val bitmap = PageImages.decode(file.readBytes(), format) ?: return null
            return Image("image/png", "png") { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val mime = bounds.outMimeType ?: return null
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        return Image(mime, extension) { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /** "Title - Chapter 12 - 3", with what a file name cannot hold taken out. */
    fun name(title: String, chapter: String, page: Int): String =
        "$title - $chapter - ${page + 1}".replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").take(120)

    /** Saves [file] to Pictures/Kozeki as [name]. False if it could not be. */
    suspend fun save(context: Context, file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val image = runCatching { image(file) }.getOrNull() ?: return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.${image.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, image.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kozeki")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext false
        runCatching {
            resolver.openOutputStream(uri)!!.use(image.write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }.onFailure { resolver.delete(uri, null, null) }.isSuccess
    }

    /** Opens the share sheet with [file] as [name]. False if it could not be. */
    suspend fun share(context: Context, file: File, name: String): Boolean {
        val shared = withContext(Dispatchers.IO) {
            runCatching {
                val image = image(file) ?: return@runCatching null
                // Only the page last shared is kept.
                val dir = File(context.cacheDir, SHARED_DIR).apply { deleteRecursively(); mkdirs() }
                val copy = File(dir, "$name.${image.extension}")
                copy.outputStream().use(image.write)
                copy to image.mime
            }.getOrNull()
        } ?: return false
        val (copy, mime) = shared
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.pages", copy)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
        return true
    }

    private const val SHARED_DIR = "shared"
}
