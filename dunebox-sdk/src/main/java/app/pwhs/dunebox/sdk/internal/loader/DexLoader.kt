package app.pwhs.dunebox.sdk.internal.loader

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources

import timber.log.Timber
import java.io.File

/**
 * Loads DEX/APK code into memory using DexClassLoader.
 *
 * For each virtual app, we create a dedicated ClassLoader that:
 * 1. Loads .dex code from the APK file
 * 2. Uses the host app's ClassLoader as parent (for Android framework classes)
 * 3. Creates separate Resources / AssetManager for the virtual app's assets
 *
 * This is the mechanism that allows us to "run" another app's code
 * inside DuneBox's process.
 */
internal class DexLoader(private val hostContext: Context) {

    /**
     * Result of loading an APK — contains the ClassLoader and Resources.
     */
    data class LoadResult(
        val classLoader: ClassLoader,
        val resources: Resources,
        val assetManager: AssetManager,
    )

    /**
     * Load an APK file's code and resources.
     *
     * @param apkPath Absolute path to the APK file
     * @param dataDir Data directory for the virtual app (for dex optimization cache)
     * @param libDir Native library directory (optional)
     * @return LoadResult containing ClassLoader and Resources, or null on failure
     */
    fun loadApk(apkPath: String, dataDir: String, libDir: String? = null): LoadResult? {
        Timber.d("Loading APK: $apkPath")

        return try {
            // 1. Ensure dex optimization directory exists
            val dexOptDir = File(dataDir, "dex_opt")
            if (!dexOptDir.exists()) {
                dexOptDir.mkdirs()
            }

            // 2. Android 10+ security: APK file MUST be read-only
            //    or DexClassLoader throws SecurityException
            val apkFile = File(apkPath)
            if (apkFile.canWrite()) {
                apkFile.setReadOnly()
                Timber.d("Set APK to read-only: $apkPath")
            }

            // 3. Create VirtualClassLoader (child-first delegation)
            // Loads classes from virtual APK FIRST, then falls back to
            // host ClassLoader for Android framework classes only.
            // This prevents Kotlin/library version conflicts.
            val classLoader = VirtualClassLoader(
                apkPath,
                dexOptDir.absolutePath,
                libDir,
                hostContext.classLoader
            )
            Timber.d("VirtualClassLoader created for: $apkPath")

            // 3. Create AssetManager and add the APK as a source
            val assetManager = createAssetManager(apkPath)
                ?: return null

            // 4. Create Resources using the custom AssetManager
            val hostResources = hostContext.resources
            val resources = Resources(
                assetManager,
                hostResources.displayMetrics,
                hostResources.configuration
            )
            Timber.d("Resources created for: $apkPath")

            LoadResult(
                classLoader = classLoader,
                resources = resources,
                assetManager = assetManager,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load APK: $apkPath")
            null
        }
    }

    /**
     * Load a specific class from a virtual app's APK.
     *
     * @param className Fully qualified class name (e.g. "com.example.MainActivity")
     * @param classLoader The ClassLoader for the virtual app
     * @return The loaded Class, or null if not found
     */
    fun loadClass(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className).also {
                Timber.d("Loaded class: $className")
            }
        } catch (e: ClassNotFoundException) {
            Timber.e(e, "Class not found: $className")
            null
        }
    }

    /**
     * Create an AssetManager that can load assets from a custom APK.
     *
     * We use reflection to call AssetManager.addAssetPath() which is hidden.
     * This is safe because we've already bypassed hidden API restrictions.
     */
    @Suppress("DiscouragedPrivateApi")
    private fun createAssetManager(apkPath: String): AssetManager? {
        return try {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getDeclaredMethod(
                "addAssetPath",
                String::class.java
            )
            addAssetPath.isAccessible = true
            val cookie = addAssetPath.invoke(assetManager, apkPath) as? Int
            if (cookie == null || cookie == 0) {
                Timber.e("addAssetPath returned invalid cookie for: $apkPath")
                return null
            }
            Timber.d("AssetManager created with cookie=$cookie for: $apkPath")
            assetManager
        } catch (e: Exception) {
            Timber.e(e, "Failed to create AssetManager for: $apkPath")
            null
        }
    }
}
