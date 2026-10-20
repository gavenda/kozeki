package dev.gavenda.kozeki.data.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionLoaderTest {

    @Test
    fun `lib version is read from the metadata when it is declared`() {
        assertEquals(1.6, ExtensionLoader.libVersion(1.6f, "1.4.12"))
    }

    @Test
    fun `lib version falls back to the version name without its last part`() {
        assertEquals(1.6, ExtensionLoader.libVersion(0f, "1.6.12"))
        assertEquals(1.4, ExtensionLoader.libVersion(0f, "1.4.1"))
    }

    @Test
    fun `lib version is unknown when neither says`() {
        assertNull(ExtensionLoader.libVersion(0f, "weird"))
    }

    @Test
    fun `only lib 1_6 is supported`() {
        val supported = listOf(1.4f, 1.5f, 1.6f).filter {
            ExtensionLoader.libVersion(it, "") == ExtensionLoader.SUPPORTED_LIB_VERSION
        }
        assertEquals(listOf(1.6f), supported)
    }

    @Test
    fun `content warning follows the new key and falls back to the old flag`() {
        assertEquals(ContentWarning.SAFE, ExtensionLoader.contentWarning(declared = 0, nsfw = 1))
        assertEquals(ContentWarning.MIXED, ExtensionLoader.contentWarning(declared = 1, nsfw = 0))
        assertEquals(ContentWarning.NSFW, ExtensionLoader.contentWarning(declared = 2, nsfw = 0))
        assertEquals(ContentWarning.NSFW, ExtensionLoader.contentWarning(declared = -1, nsfw = 1))
        assertEquals(ContentWarning.SAFE, ExtensionLoader.contentWarning(declared = -1, nsfw = 0))
    }
}
