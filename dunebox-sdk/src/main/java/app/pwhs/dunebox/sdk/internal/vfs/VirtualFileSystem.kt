package app.pwhs.dunebox.sdk.internal.vfs

import app.pwhs.dunebox.native_engine.NativeEngine
import app.pwhs.dunebox.sdk.rule.IORules
import timber.log.Timber
import java.io.File

/**
 * Manages the virtual file system for cloned apps.
 * Creates isolated directories and applies IO redirect rules via native hooks.
 *
 * Directory structure:
 * <app_internal>/virtual/<userId>/<packageName>/
 *   ├── data/        (SharedPreferences, databases, etc.)
 *   ├── cache/       (Cache files)
 *   └── files/       (App-specific files)
 */
internal class VirtualFileSystem(
    private val baseDir: File
) {

    /**
     * Initialize the VFS subsystem.
     */
    fun init() {
        NativeEngine.init()
        Timber.d("VFS initialized with base: ${baseDir.absolutePath}")
    }

    /**
     * Create the virtual directory structure for a cloned app.
     */
    fun createVirtualDirs(packageName: String, userId: Int = 0) {
        val appRoot = getAppRoot(packageName, userId)
        val dirs = listOf(
            File(appRoot, "data"),
            File(appRoot, "cache"),
            File(appRoot, "files"),
            File(appRoot, "databases"),
            File(appRoot, "shared_prefs"),
            File(appRoot, "code_cache"),
            File(appRoot, "lib"),
        )

        dirs.forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Timber.d("Created virtual dir: ${dir.absolutePath}")
            }
        }
    }

    /**
     * Setup IO redirect rules for a virtual app.
     * Redirects all data paths from the original package to the virtual directory.
     */
    fun setupRedirect(packageName: String, userId: Int = 0) {
        val virtualRoot = getAppRoot(packageName, userId).absolutePath

        // Standard data directories that need redirection
        NativeEngine.addRedirectRule(
            "/data/data/$packageName",
            virtualRoot
        )
        NativeEngine.addRedirectRule(
            "/data/user/0/$packageName",
            virtualRoot
        )

        Timber.d("IO redirect setup for $packageName -> $virtualRoot")
    }

    /**
     * Apply custom IO rules from a PackageRule.
     */
    fun applyIORules(ioRules: IORules) {
        ioRules.denyPaths.forEach { path ->
            NativeEngine.addDenyRule(path)
        }

        ioRules.redirectPaths.forEach { entry ->
            NativeEngine.addRedirectRule(entry.from, entry.to)
        }

        Timber.d("Applied ${ioRules.denyPaths.size} deny + ${ioRules.redirectPaths.size} redirect IO rules")
    }

    /**
     * Start IO redirect (activates native hooks).
     */
    fun startRedirect(): Boolean = NativeEngine.startRedirect()

    /**
     * Stop IO redirect.
     */
    fun stopRedirect() = NativeEngine.stopRedirect()

    /**
     * Delete all virtual data for a cloned app.
     */
    fun deleteVirtualData(packageName: String, userId: Int = 0) {
        val appRoot = getAppRoot(packageName, userId)
        if (appRoot.exists()) {
            appRoot.deleteRecursively()
            Timber.d("Deleted virtual data: ${appRoot.absolutePath}")
        }
    }

    /**
     * Get the root directory for a virtual app instance.
     */
    fun getAppRoot(packageName: String, userId: Int = 0): File {
        return File(baseDir, "$userId/$packageName")
    }

    /**
     * Get the data directory for a virtual app (equivalent to /data/data/pkg).
     */
    fun getDataDir(packageName: String, userId: Int = 0): File {
        return getAppRoot(packageName, userId)
    }

    /**
     * Get the APK storage directory (where cloned APKs are stored).
     */
    fun getApkDir(): File {
        val dir = File(baseDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
