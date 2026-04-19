package app.pwhs.dunebox.sdk.model

import android.graphics.drawable.Drawable

/**
 * Information about a virtual (cloned) application.
 */
data class VirtualAppInfo(
    /** Original package name of the cloned app */
    val packageName: String,

    /** Display name of the app */
    val appName: String,

    /** Version name (e.g., "1.0.0") */
    val versionName: String,

    /** Version code */
    val versionCode: Long,

    /** Virtual user ID (0 = default, 1+ = additional instances) */
    val userId: Int,

    /** Path to the APK file within DuneBox's storage */
    val apkPath: String,

    /** Path to the virtual data directory */
    val dataDir: String,

    /** App icon drawable (loaded from APK) */
    val icon: Drawable? = null,

    /** Timestamp when the app was cloned */
    val installedAt: Long = System.currentTimeMillis(),

    /** Timestamp when the app was last launched */
    val lastLaunchedAt: Long? = null,
)
