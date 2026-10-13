package dev.gavenda.kozeki.data.manga

import eu.kanade.tachiyomi.source.CatalogueSource

/** What a Mihon extension app says about its own content. Shown, never used to filter. */
enum class ContentWarning { SAFE, MIXED, NSFW }

/** An installed app that declares itself a Mihon extension, whether or not it could be loaded. */
sealed interface MangaExtension {
    val name: String
    val packageName: String
    val versionName: String
    val versionCode: Long
}

/** An extension whose sources are ready to be searched. */
data class LoadedExtension(
    override val name: String,
    override val packageName: String,
    override val versionName: String,
    override val versionCode: Long,
    val contentWarning: ContentWarning,
    /** Only the sources that can be browsed and searched. */
    val sources: List<CatalogueSource>,
) : MangaExtension

/** An extension that was found but could not be used, with the reason to show for it. */
data class BrokenExtension(
    override val name: String,
    override val packageName: String,
    override val versionName: String,
    override val versionCode: Long,
    val reason: Reason,
) : MangaExtension {

    sealed interface Reason {
        /** Built against an extension library this app cannot run. [libVersion] is null when it declares none. */
        data class UnsupportedLib(val libVersion: Double?) : Reason

        /** Its package is missing something an extension has to have. */
        data object Malformed : Reason

        /** Threw while its classes or sources were being created. */
        data class Failed(val message: String) : Reason
    }
}
