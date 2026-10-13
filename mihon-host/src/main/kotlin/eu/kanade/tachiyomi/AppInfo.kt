package eu.kanade.tachiyomi

/**
 * Used by extensions.
 *
 * The host app says who it is through [install] before any extension is loaded.
 */
@Suppress("UNUSED")
object AppInfo {
    private var versionCode: Int = 0
    private var versionName: String = ""

    fun install(versionCode: Int, versionName: String) {
        this.versionCode = versionCode
        this.versionName = versionName
    }

    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode(): Int = versionCode

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionName(): String = versionName

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/avif",
        "image/gif",
        "image/heif",
        "image/jpeg",
        "image/jxl",
        "image/png",
        "image/webp",
    )
}
