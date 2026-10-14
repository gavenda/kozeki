package dev.gavenda.kozeki.data.manga

import android.graphics.Bitmap
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.awxkee.jxlcoder.JxlCoder
import com.radzivon.bartoshyk.avif.coder.Coder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import com.radzivon.bartoshyk.avif.coder.ScaleMode
import com.radzivon.bartoshyk.avif.coder.ScalingQuality
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The formats of a manga page the platform cannot be trusted with. Android decodes AVIF through its
 * AV1 hardware codec, which refuses a webtoon's height, and does not decode JPEG XL at all, so
 * these are decoded in software instead. Every other format is left to the platform.
 */
internal object PageImages {

    enum class Format { Avif, Jxl }

    private val avif = Coder()

    /** The largest a page is decoded to: about 100 MB of pixels. */
    private const val MAX_PIXELS = 25_000_000L

    /** The format a file starting with [header] is, if it is one of these. */
    fun sniff(header: ByteArray): Format? {
        if (header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0x0A.toByte()) return Format.Jxl
        if (header.size >= 12 && header[4] == 'J'.code.toByte() && header[5] == 'X'.code.toByte() &&
            header[6] == 'L'.code.toByte() && header[7] == ' '.code.toByte()
        ) {
            return Format.Jxl
        }
        if (header.size >= 12 && String(header, 4, 4, Charsets.ISO_8859_1) == "ftyp") {
            return when (String(header, 8, 4, Charsets.ISO_8859_1)) {
                "avif", "avis", "heic", "heix", "hevc", "mif1", "msf1" -> Format.Avif
                else -> null
            }
        }
        return null
    }

    fun sniff(file: File): Format? = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(32)
            sniff(header.copyOf(input.read(header).coerceAtLeast(0)))
        }
    }.getOrNull()

    /** The size of the image in [file], read from its header. */
    fun size(file: File, format: Format): Size? = runCatching { size(file.readBytes(), format) }.getOrNull()

    private fun size(bytes: ByteArray, format: Format): Size? = when (format) {
        Format.Avif -> avif.getSize(bytes)
        Format.Jxl -> JxlCoder.getSize(bytes)
    }?.takeIf { it.width > 0 && it.height > 0 }

    /** Decodes [bytes], no wider than [width] if given, and never past [MAX_PIXELS]. */
    fun decode(bytes: ByteArray, format: Format, width: Int? = null): Bitmap? {
        val natural = size(bytes, format) ?: return null
        var scale = if (width != null) minOf(1f, width.toFloat() / natural.width) else 1f
        val pixels = natural.width.toLong() * natural.height
        if (pixels * scale * scale > MAX_PIXELS) scale = sqrt(MAX_PIXELS.toFloat() / pixels)
        val w = (natural.width * scale).roundToInt().coerceAtLeast(1)
        val h = (natural.height * scale).roundToInt().coerceAtLeast(1)
        return when (format) {
            Format.Avif -> avif.decodeSampled(
                bytes, w, h, PreferredColorConfig.RGBA_8888, ScaleMode.RESIZE, ScalingQuality.DEFAULT,
            )

            Format.Jxl -> JxlCoder.decodeSampled(
                bytes, w, h, com.awxkee.jxlcoder.PreferredColorConfig.RGBA_8888,
                com.awxkee.jxlcoder.ScaleMode.RESIZE, com.awxkee.jxlcoder.JxlResizeFilter.BILINEAR,
            )
        }
    }

    /** Lets the image loader show these formats, for the pages that are not read as a strip. */
    class CoilDecoder(private val source: ImageSource, private val format: Format) : Decoder {
        override suspend fun decode(): DecodeResult? {
            val bytes = source.source().readByteArray()
            val bitmap = decode(bytes, format) ?: return null
            return DecodeResult(bitmap.asImage(), isSampled = false)
        }

        class Factory : Decoder.Factory {
            override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
                val peek = result.source.source().peek()
                peek.request(32)
                val header = peek.buffer.readByteArray(minOf(32L, peek.buffer.size))
                val format = sniff(header) ?: return null
                return CoilDecoder(result.source, format)
            }
        }
    }
}
