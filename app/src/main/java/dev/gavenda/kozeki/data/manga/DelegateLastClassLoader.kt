package dev.gavenda.kozeki.data.manga

import android.os.Build
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * The class loader an extension's classes are loaded with: its own dex first, then the app's, so an
 * extension can bring a newer copy of a library without breaking the app.
 *
 * Ported from Mihon's `DelegateLastClassLoaderCompat`. ART recognises the built-in loader and maps
 * app images compiled for this app's class loader chain, and before Android 17 a damaged one
 * crashes every launch until the app is updated. ART doesn't recognise the backport.
 */
@Suppress("FunctionName")
internal fun DelegateLastClassLoaderCompat(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
): ClassLoader =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        DelegateLastClassLoader(dexPath, librarySearchPath, parent)
    } else {
        DelegateLastPathClassLoader(dexPath, librarySearchPath, parent)
    }

/**
 * A [PathClassLoader] that looks up classes like [DelegateLastClassLoader]: the boot class loader
 * first, then its own dex path, then the parent.
 */
private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        val fromSelf = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }

        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromSelf
        }
    }

    override fun getResource(name: String?): URL? =
        bootClassLoader?.getResource(name) ?: findResource(name) ?: parent?.getResource(name)

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
