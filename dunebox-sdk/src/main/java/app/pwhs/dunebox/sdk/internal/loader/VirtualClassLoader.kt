package app.pwhs.dunebox.sdk.internal.loader

import dalvik.system.DexClassLoader
import timber.log.Timber

/**
 * Child-first (reverse parent delegation) ClassLoader for virtual apps.
 *
 * Standard Java ClassLoader: parent first → child
 * VirtualClassLoader:        child first → parent (only for framework classes)
 *
 * Why this is needed:
 * Virtual apps may bundle their own versions of libraries (Kotlin, Gson, etc.)
 * that differ from what DuneBox uses. If we use standard parent delegation,
 * the host's version of these libraries would be loaded, causing:
 * - NoSuchMethodError (e.g. Kotlin Intrinsics.e() missing)
 * - ClassCastException (different class versions)
 * - IncompatibleClassChangeError
 *
 * By loading from the virtual APK first, we ensure the virtual app uses
 * its own bundled dependencies.
 */
internal class VirtualClassLoader(
    apkPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    parent: ClassLoader
) : DexClassLoader(apkPath, optimizedDirectory, librarySearchPath, parent) {

    companion object {
        /**
         * Prefixes that should ALWAYS be loaded from parent (Android framework).
         * Virtual apps should never override these.
         */
        private val PARENT_FIRST_PREFIXES = arrayOf(
            "java.",
            "javax.",
            "android.",
            "androidx.annotation.",     // Keep annotation compat from host
            "dalvik.",
            "com.android.",
            "org.apache.",              // XML parsers etc.
            "org.json.",
            "org.xmlpull.",
            "sun.",
            "libcore.",
        )

        /**
         * Check if a class should be loaded from parent first.
         */
        private fun shouldLoadFromParent(name: String): Boolean {
            for (prefix in PARENT_FIRST_PREFIXES) {
                if (name.startsWith(prefix)) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Child-first class loading:
     * 1. If it's a framework class → load from parent (normal delegation)
     * 2. Otherwise → try loading from THIS ClassLoader (the virtual APK) first
     * 3. If not found in APK → fall back to parent
     */
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 1. Check if already loaded
        var clazz = findLoadedClass(name)
        if (clazz != null) return clazz

        // 2. Framework classes: always load from parent
        if (shouldLoadFromParent(name)) {
            return super.loadClass(name, resolve)
        }

        // 3. Try loading from virtual APK first (child-first)
        try {
            clazz = findClass(name)
            if (clazz != null) {
                return clazz
            }
        } catch (_: ClassNotFoundException) {
            // Not found in virtual APK, fall through to parent
        }

        // 4. Fallback to parent
        return super.loadClass(name, resolve)
    }
}
