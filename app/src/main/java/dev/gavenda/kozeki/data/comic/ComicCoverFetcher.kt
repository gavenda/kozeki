package dev.gavenda.kozeki.data.comic

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options

/** Loads a [ComicCover] URL through its source, which a plain network fetch would get refused. */
class ComicCoverFetcher(
    private val images: ComicImages,
    private val cover: ComicCover.Parsed,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val body = images.open(cover.sourceId, cover.url).body
        return SourceFetchResult(
            source = ImageSource(body.source(), options.fileSystem),
            mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" },
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val images: () -> ComicImages) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val cover = ComicCover.parse(data.toString()) ?: return null
            return ComicCoverFetcher(images(), cover, options)
        }
    }
}
