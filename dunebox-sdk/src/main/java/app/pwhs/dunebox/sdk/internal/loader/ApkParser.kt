package app.pwhs.dunebox.sdk.internal.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import timber.log.Timber
import java.io.File

/**
 * Data class holding parsed APK information.
 */
internal data class ParsedApkInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val mainActivity: String?,
    val applicationClassName: String?,
    val icon: Drawable?,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
)

/**
 * Parses APK files to extract package information, components, and icons.
 * Uses Android's built-in PackageManager for reliable parsing.
 */
internal class ApkParser(private val context: Context) {

    /**
     * Parse an APK file and extract all relevant information.
     */
    fun parse(apkFile: File): ParsedApkInfo? {
        if (!apkFile.exists()) {
            Timber.e("APK file does not exist: ${apkFile.absolutePath}")
            return null
        }

        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_META_DATA

            val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: run {
                    Timber.e("Failed to parse APK: ${apkFile.absolutePath}")
                    return null
                }

            // Set source/public source dir for icon loading
            packageInfo.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
            }

            val appInfo = packageInfo.applicationInfo
            val appName = appInfo?.let {
                pm.getApplicationLabel(it).toString()
            } ?: packageInfo.packageName

            val icon = appInfo?.let {
                try {
                    pm.getApplicationIcon(it)
                } catch (_: Exception) {
                    null
                }
            }

            // Find main launcher activity
            val mainActivity = packageInfo.activities?.firstOrNull { activity ->
                // Simple heuristic: look for common main activity names
                activity.name.endsWith("MainActivity") ||
                        activity.name.endsWith(".Main") ||
                        activity.exported
            }?.name

            ParsedApkInfo(
                packageName = packageInfo.packageName,
                appName = appName,
                versionName = packageInfo.versionName ?: "1.0",
                versionCode = packageInfo.longVersionCode,
                mainActivity = mainActivity,
                applicationClassName = appInfo?.className,
                icon = icon,
                permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
                activities = packageInfo.activities?.map { it.name } ?: emptyList(),
                services = packageInfo.services?.map { it.name } ?: emptyList(),
                receivers = packageInfo.receivers?.map { it.name } ?: emptyList(),
                providers = packageInfo.providers?.map { it.name } ?: emptyList(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing APK: ${apkFile.absolutePath}")
            null
        }
    }

    /**
     * Parse an already-installed package by name.
     */
    fun parseInstalled(packageName: String): ParsedApkInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val apkFile = File(appInfo.sourceDir)
            parse(apkFile)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("Package not found: $packageName")
            null
        }
    }

    /**
     * Get the APK path for an installed package.
     */
    fun getApkPath(packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appInfo.sourceDir
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
