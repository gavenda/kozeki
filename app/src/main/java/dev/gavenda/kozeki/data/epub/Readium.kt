package dev.gavenda.kozeki.data.epub

import android.app.Application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** The one place that knows how to turn a file into a Readium [Publication]. */
class Readium(application: Application) {

    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(application.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        DefaultPublicationParser(application, httpClient, assetRetriever, pdfFactory = null),
    )

    /**
     * Opens [file] as an EPUB. The caller owns the returned publication and must close it.
     *
     * @throws OpenException when the file is not a readable, DRM-free EPUB.
     */
    suspend fun open(file: File): Publication = withContext(Dispatchers.IO) {
        val asset = assetRetriever.retrieve(file).getOrElse {
            throw OpenException(OpenFailure.NOT_AN_EPUB, it.message)
        }
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            throw OpenException(OpenFailure.NOT_AN_EPUB, it.message)
        }
        if (!publication.conformsTo(Publication.Profile.EPUB)) {
            publication.close()
            throw OpenException(OpenFailure.NOT_AN_EPUB, "Not an EPUB publication")
        }
        // Only Readium LCP could be unlocked, and that needs a private library this app does not ship.
        if (publication.isProtected) {
            publication.close()
            throw OpenException(OpenFailure.PROTECTED, "The EPUB is protected by DRM")
        }
        publication
    }

    enum class OpenFailure { NOT_AN_EPUB, PROTECTED }

    class OpenException(val failure: OpenFailure, message: String) : Exception(message)
}
