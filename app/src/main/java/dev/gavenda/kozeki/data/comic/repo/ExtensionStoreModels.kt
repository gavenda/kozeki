package dev.gavenda.kozeki.data.comic.repo

import dev.gavenda.kozeki.data.comic.ContentWarning
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** A repository of Mihon extensions, as read from its protobuf index. */
data class ExtensionStore(
    /** The URL the index is read from. */
    val indexUrl: String,
    val name: String,
    /** SHA-256 of the certificate its APKs are signed with, lower-case hex. */
    val signingKey: String,
    val website: String?,
    val extensions: List<AvailableExtension>,
)

/** One extension a repository offers. */
data class AvailableExtension(
    val name: String,
    val packageName: String,
    val apkUrl: String,
    val iconUrl: String,
    val libVersion: Double?,
    val versionCode: Long,
    val versionName: String,
    /** A language tag, or "all" for one in several languages. */
    val language: String,
    val contentWarning: ContentWarning,
    val sourceNames: List<String>,
)

// Ported from Mihon's `mihon.data.extension.model.NetworkExtensionStore`, protobuf only.

/** The index of a repository. Its list of extensions is either in it or at [extensionListUrl]. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: Warning = Warning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(7) val message: String? = null,
    )

    @Suppress("unused")
    @Serializable
    enum class Warning {
        @ProtoNumber(0) UNSPECIFIED,
        @ProtoNumber(1) SAFE,
        @ProtoNumber(2) MIXED,
        @ProtoNumber(3) NSFW,
    }
}

internal fun NetworkExtensionStore.ExtensionList.toAvailable(): List<AvailableExtension> = extensions.map { ext ->
    val languages = ext.sources.map { it.language }.toSet()
    AvailableExtension(
        name = ext.name,
        packageName = ext.packageName,
        apkUrl = ext.resources.apkUrl,
        iconUrl = ext.resources.iconUrl,
        libVersion = ext.extensionLib.toDoubleOrNull(),
        versionCode = ext.versionCode,
        versionName = ext.versionName,
        language = languages.singleOrNull() ?: "all",
        contentWarning = when (ext.contentWarning) {
            NetworkExtensionStore.Warning.MIXED -> ContentWarning.MIXED
            NetworkExtensionStore.Warning.NSFW -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        },
        sourceNames = ext.sources.map { it.name }.distinct(),
    )
}

/** A fingerprint as lower-case hex with no separators, so two spellings of one key compare equal. */
internal fun String.normalizedFingerprint(): String = filter { it.isLetterOrDigit() }.lowercase()
