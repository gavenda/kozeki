package dev.gavenda.kozeki.data.comic

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Finds the Mihon extensions installed on the device and creates their sources. A port of the shared
 * (system-installed) half of Mihon's ExtensionLoader.
 *
 * Extensions Mihon keeps in its own private directory cannot be read from another app, so only
 * ones installed through the system are found. They run with no signature or trust check: every
 * extension on the device is loaded.
 */
internal class ExtensionLoader(private val context: Context) {

    /**
     * Every installed extension, each either loaded or with the reason it was not.
     *
     * @param alreadyLoaded Extensions from an earlier call. One whose package is unchanged is
     * returned as it is, so its sources stay the same objects.
     */
    suspend fun load(alreadyLoaded: Map<String, LoadedExtension> = emptyMap()): List<ComicExtension> =
        withContext(Dispatchers.IO) {
            val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
            context.packageManager
                .getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                .filter(::isExtension)
                .map { async { loadCatching(it, alreadyLoaded[it.packageName]) } }
                .awaitAll()
                .sortedBy { it.name.lowercase() }
        }

    /** An apk can be malformed in ways [loadExtension] has no name for. That must not stop the rest. */
    private fun loadCatching(pkg: PackageInfo, alreadyLoaded: LoadedExtension?): ComicExtension =
        try {
            loadExtension(pkg, alreadyLoaded)
        } catch (e: Throwable) {
            Log.e(TAG, "Extension load error: ${pkg.packageName}", e)
            BrokenExtension(
                name = pkg.packageName,
                packageName = pkg.packageName,
                versionName = pkg.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(pkg),
                reason = BrokenExtension.Reason.Failed(e.rootMessage),
            )
        }

    private fun loadExtension(pkg: PackageInfo, alreadyLoaded: LoadedExtension?): ComicExtension {
        val appInfo = pkg.applicationInfo
        val metaData = appInfo?.metaData
        val name = metaData?.getString(METADATA_NAME)
            ?: appInfo?.let { context.packageManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ") }
            ?: pkg.packageName
        val versionName = pkg.versionName.orEmpty()
        val versionCode = PackageInfoCompat.getLongVersionCode(pkg)

        fun broken(reason: BrokenExtension.Reason) =
            BrokenExtension(name, pkg.packageName, versionName, versionCode, reason)

        if (appInfo == null || metaData == null || versionName.isEmpty()) {
            Log.w(TAG, "Missing application info or version for extension $name")
            return broken(BrokenExtension.Reason.Malformed)
        }

        val libVersion = libVersion(metaData.getFloat(METADATA_EXTENSION_LIB), versionName)
        if (libVersion != SUPPORTED_LIB_VERSION) {
            Log.w(TAG, "$name uses extension lib $libVersion, only $SUPPORTED_LIB_VERSION is supported")
            return broken(BrokenExtension.Reason.UnsupportedLib(libVersion))
        }

        if (alreadyLoaded != null && alreadyLoaded.versionCode == versionCode) return alreadyLoaded

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
        if (sourceClasses.isNullOrBlank()) {
            Log.w(TAG, "Missing source class for extension $name")
            return broken(BrokenExtension.Reason.Malformed)
        }

        val classLoader = DelegateLastClassLoaderCompat(appInfo.sourceDir, null, context.classLoader)
        val sources = try {
            sourceClasses.split(";").map { it.trim() }.flatMap { className ->
                val fullName = if (className.startsWith(".")) pkg.packageName + className else className
                when (val obj = Class.forName(fullName, false, classLoader).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(obj)
                    is SourceFactory -> obj.createSources()
                    else -> error("Unknown source class type: ${obj.javaClass}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Extension load error: $name", e)
            return broken(BrokenExtension.Reason.Failed(e.rootMessage))
        }

        return LoadedExtension(
            name = name,
            packageName = pkg.packageName,
            versionName = versionName,
            versionCode = versionCode,
            contentWarning = contentWarning(metaData.getInt(METADATA_CONTENT_WARNING, -1), metaData.getInt(METADATA_NSFW)),
            sources = sources.filterIsInstance<CatalogueSource>(),
        )
    }

    private fun isExtension(pkg: PackageInfo): Boolean =
        pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    companion object {
        private const val TAG = "ExtensionLoader"

        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
        private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

        /** The one extension library version this app can run. Older ones are on their way out. */
        const val SUPPORTED_LIB_VERSION = 1.6

        /**
         * The extension library an extension was built against: what it declares in its metadata,
         * else the version name without its last part ("1.6.12" is lib 1.6).
         */
        fun libVersion(declared: Float, versionName: String): Double? =
            declared.takeUnless { it == 0.0f }?.toString()?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()

        /** [declared] is the `tachiyomix.contentWarning` value or -1 without one, [nsfw] the legacy flag. */
        fun contentWarning(declared: Int, nsfw: Int): ContentWarning = when {
            declared == 1 -> ContentWarning.MIXED
            declared == 2 -> ContentWarning.NSFW
            declared >= 0 -> ContentWarning.SAFE
            nsfw == 1 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }
    }
}

/** The message of the deepest cause, which is the one that says what went wrong. */
private val Throwable.rootMessage: String
    get() {
        val root = generateSequence(this) { it.cause }.last()
        return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
    }
