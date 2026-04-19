package app.pwhs.dunebox.sdk.internal.engine

import android.content.ComponentName
import app.pwhs.dunebox.sdk.internal.loader.ParsedApkInfo
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry of installed virtual apps.
 * Maintains mappings between package names, APK info, and loaded state.
 *
 * Thread-safe: all operations use ConcurrentHashMap.
 */
internal object VirtualAppRegistry {

    /**
     * Represents a registered virtual app with all its metadata.
     */
    data class AppEntry(
        val packageName: String,
        val userId: Int,
        val apkPath: String,
        val dataDir: String,
        val parsedInfo: ParsedApkInfo,
        /** ClassLoader for this app, created lazily when the app is launched */
        var classLoader: ClassLoader? = null,
        /** Guest Application instance after GuestApplicationLoader.loadIfNeeded */
        var application: android.app.Application? = null,
    )

    // PackageName -> AppEntry
    private val apps = ConcurrentHashMap<String, AppEntry>()

    // Track which package is currently "active" (launched)
    @Volatile
    var activePackage: String? = null
        private set

    /**
     * Register a virtual app after installation.
     */
    fun register(entry: AppEntry) {
        apps[entry.packageName] = entry
        Timber.d("Registered virtual app: ${entry.packageName}")
    }

    /**
     * Unregister a virtual app.
     */
    fun unregister(packageName: String) {
        apps.remove(packageName)
        if (activePackage == packageName) {
            activePackage = null
        }
        Timber.d("Unregistered virtual app: $packageName")
    }

    /**
     * Get app entry by package name.
     */
    fun getApp(packageName: String): AppEntry? = apps[packageName]

    /**
     * Get the currently active (launched) app entry.
     */
    fun getActiveApp(): AppEntry? = activePackage?.let { apps[it] }

    /**
     * Set the currently active package.
     */
    fun setActivePackage(packageName: String?) {
        activePackage = packageName
        Timber.d("Active package: $packageName")
    }

    /**
     * Check if a component (Activity/Service class name) belongs to a virtual app.
     */
    fun isVirtualComponent(className: String): Boolean {
        return apps.values.any { entry ->
            entry.parsedInfo.activities.contains(className) ||
                    entry.parsedInfo.services.contains(className) ||
                    entry.parsedInfo.receivers.contains(className) ||
                    entry.parsedInfo.providers.contains(className)
        }
    }

    /**
     * Find which virtual app owns a given component.
     */
    fun findAppByComponent(className: String): AppEntry? {
        return apps.values.find { entry ->
            entry.parsedInfo.activities.contains(className) ||
                    entry.parsedInfo.services.contains(className)
        }
    }

    /**
     * Find which virtual app owns a given component by ComponentName.
     */
    fun findAppByComponent(component: ComponentName): AppEntry? {
        return apps[component.packageName]
    }

    /**
     * Check if a package is registered.
     */
    fun isRegistered(packageName: String): Boolean = apps.containsKey(packageName)

    /**
     * Get all registered apps.
     */
    fun getAllApps(): List<AppEntry> = apps.values.toList()
}
