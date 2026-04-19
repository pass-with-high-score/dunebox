package app.pwhs.dunebox.sdk.internal.loader

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import timber.log.Timber
import java.io.File

/**
 * Virtual Context wrapping the host context.
 *
 * This is the critical deception layer — when a virtual app calls:
 * - getPackageName() → returns the virtual package name, not DuneBox
 * - getClassLoader() → returns the DexClassLoader with virtual APK code
 * - getResources() → returns Resources pointing to virtual APK assets
 * - getFilesDir() → returns the sandboxed virtual data directory
 * - getSharedPreferences() → reads from sandboxed preferences
 * - getCacheDir() → returns sandboxed cache directory
 * - getDatabasePath() → returns sandboxed DB path
 *
 * Without this, the virtual app would think it IS DuneBox and use
 * DuneBox's resources/data, causing crashes and data corruption.
 */
internal class VirtualContext(
    base: Context,
    private val virtualPackageName: String,
    private val virtualClassLoader: ClassLoader,
    private val virtualResources: Resources,
    private val virtualAssetManager: AssetManager,
    private val virtualDataDir: File,
    private val virtualApkPath: String,
) : ContextWrapper(base) {

    // ============================================================
    // Package Identity — make virtual app think it's itself
    // ============================================================

    override fun getPackageName(): String = virtualPackageName

    override fun getOpPackageName(): String = virtualPackageName

    // ============================================================
    // ClassLoader — return virtual APK's ClassLoader
    // ============================================================

    override fun getClassLoader(): ClassLoader = virtualClassLoader

    // ============================================================
    // Resources — return virtual APK's resources
    // ============================================================

    override fun getResources(): Resources = virtualResources

    override fun getAssets(): AssetManager = virtualAssetManager

    override fun getTheme(): Resources.Theme {
        val theme = virtualResources.newTheme()
        // Apply default Android theme
        theme.applyStyle(android.R.style.Theme_Material_Light_DarkActionBar, true)
        return theme
    }

    // ============================================================
    // File System — redirect to sandboxed paths
    // ============================================================

    override fun getFilesDir(): File {
        val dir = File(virtualDataDir, "files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getCacheDir(): File {
        val dir = File(virtualDataDir, "cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getCodeCacheDir(): File {
        val dir = File(virtualDataDir, "code_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getNoBackupFilesDir(): File {
        val dir = File(virtualDataDir, "no_backup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getExternalFilesDir(type: String?): File? {
        val dir = if (type != null) {
            File(virtualDataDir, "external_files/$type")
        } else {
            File(virtualDataDir, "external_files")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getExternalCacheDir(): File? {
        val dir = File(virtualDataDir, "external_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getDir(name: String, mode: Int): File {
        val dir = File(virtualDataDir, "app_$name")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getDatabasePath(name: String): File {
        val dbDir = File(virtualDataDir, "databases")
        if (!dbDir.exists()) dbDir.mkdirs()
        return File(dbDir, name)
    }

    override fun getDataDir(): File = virtualDataDir

    // ============================================================
    // ApplicationInfo — spoof apk path
    // ============================================================

    override fun getApplicationInfo(): ApplicationInfo {
        val info = ApplicationInfo(super.getApplicationInfo())
        info.packageName = virtualPackageName
        info.sourceDir = virtualApkPath
        info.publicSourceDir = virtualApkPath
        info.dataDir = virtualDataDir.absolutePath
        info.nativeLibraryDir = File(virtualDataDir, "lib").absolutePath
        return info
    }

    // ============================================================
    // Context creation — ensure derived contexts also use virtual overrides
    // ============================================================

    override fun createPackageContext(packageName: String, flags: Int): Context {
        // If requesting the virtual package context, return this
        if (packageName == virtualPackageName) {
            return this
        }
        return super.createPackageContext(packageName, flags)
    }

    override fun getApplicationContext(): Context = this

    override fun toString(): String {
        return "VirtualContext{pkg=$virtualPackageName, data=${virtualDataDir.absolutePath}}"
    }
}
