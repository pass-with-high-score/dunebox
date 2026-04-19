package app.pwhs.dunebox.sdk.internal.hook

import android.content.pm.ApplicationInfo
import app.pwhs.dunebox.sdk.internal.engine.VirtualAppRegistry
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Manages virtual LoadedApk instances in ActivityThread.mPackages.
 *
 * When the framework launches an Activity, it calls:
 *   ActivityThread.getPackageInfoNoCheck(applicationInfo, compatInfo)
 * which looks up mPackages map for a LoadedApk matching the package name.
 *
 * If we register a LoadedApk with the virtual app's package name and
 * a ClassLoader that can load classes from the virtual APK, the framework
 * will use it automatically.
 */
internal object LoadedApkHook {

    private val registeredPackages = mutableSetOf<String>()

    fun registerVirtualPackage(appEntry: VirtualAppRegistry.AppEntry): Boolean {
        if (appEntry.packageName in registeredPackages) {
            Timber.d("LoadedApk already registered for: ${appEntry.packageName}")
            return true
        }

        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentATMethod = ReflectUtils.getMethod(atClass, "currentActivityThread")
                ?: throw RuntimeException("Cannot find currentActivityThread()")
            val activityThread = currentATMethod.invoke(null)
                ?: throw RuntimeException("currentActivityThread() returned null")

            // 1. Get mPackages
            val mPackagesField = ReflectUtils.getField(atClass, "mPackages")
                ?: throw RuntimeException("Cannot find mPackages field")
            val mPackages = mPackagesField.get(activityThread)
                ?: throw RuntimeException("mPackages is null")

            // Get the host package name via reflection
            val currentPkgMethod = ReflectUtils.getMethod(atClass, "currentPackageName")
            val hostPkgName = currentPkgMethod?.invoke(null) as? String
                ?: throw RuntimeException("Cannot get current package name")

            // 2. Get host's LoadedApk as template (for uid, flags etc.)
            val getMethod = mPackages.javaClass.getMethod("get", Any::class.java)
            val hostLoadedApkRef = getMethod.invoke(mPackages, hostPkgName) as? WeakReference<*>
            val hostLoadedApk = hostLoadedApkRef?.get()
                ?: throw RuntimeException("Host LoadedApk not found")

            val loadedApkClass = hostLoadedApk.javaClass

            // 3. Create ApplicationInfo for the virtual app
            val virtualAppInfo = ApplicationInfo()
            virtualAppInfo.packageName = appEntry.packageName
            virtualAppInfo.sourceDir = appEntry.apkPath
            virtualAppInfo.publicSourceDir = appEntry.apkPath
            virtualAppInfo.dataDir = appEntry.dataDir
            virtualAppInfo.nativeLibraryDir = "${appEntry.dataDir}/lib"

            // Copy host's uid/flags via reflection
            val hostAppInfoField = ReflectUtils.getField(loadedApkClass, "mApplicationInfo")
            val hostAppInfo = hostAppInfoField?.get(hostLoadedApk) as? ApplicationInfo
            if (hostAppInfo != null) {
                virtualAppInfo.uid = hostAppInfo.uid
                virtualAppInfo.targetSdkVersion = hostAppInfo.targetSdkVersion
                virtualAppInfo.flags = hostAppInfo.flags
            }

            // 4. Call getPackageInfoNoCheck to create LoadedApk
            val compatInfoClass = Class.forName("android.content.res.CompatibilityInfo")
            val defaultCompatField = compatInfoClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO")
            defaultCompatField.isAccessible = true
            val defaultCompat = defaultCompatField.get(null)

            val getPackageInfoMethod = atClass.getDeclaredMethod(
                "getPackageInfoNoCheck",
                ApplicationInfo::class.java,
                compatInfoClass
            )
            getPackageInfoMethod.isAccessible = true

            val virtualLoadedApk = getPackageInfoMethod.invoke(
                activityThread, virtualAppInfo, defaultCompat
            ) ?: throw RuntimeException("getPackageInfoNoCheck returned null")

            // 5. Replace ClassLoader
            val mClassLoaderField = ReflectUtils.getField(loadedApkClass, "mClassLoader")
            mClassLoaderField?.set(virtualLoadedApk, appEntry.classLoader)

            val mDefaultCLField = ReflectUtils.getField(loadedApkClass, "mDefaultClassLoader")
            mDefaultCLField?.set(virtualLoadedApk, appEntry.classLoader)

            // 6. Set mResDir — CRITICAL for Resources to find drawables
            val mResDirField = ReflectUtils.getField(loadedApkClass, "mResDir")
            mResDirField?.set(virtualLoadedApk, appEntry.apkPath)
            Timber.d("mResDir set to: ${appEntry.apkPath}")

            // 7. Set mSplitResDirs to empty (no split APKs)
            val mSplitResDirsField = ReflectUtils.getField(loadedApkClass, "mSplitResDirs")
            if (mSplitResDirsField != null) {
                mSplitResDirsField.set(virtualLoadedApk, null)
            }

            // 8. Do NOT pre-init Resources. Creating Resources via the deprecated
            // constructor bypasses ResourcesManager, so ResourcesKey / mResDir-backed
            // fields on ResourcesImpl are not registered. On MIUI + MediaTek this
            // triggers a NPE in AsyncDrawableCache.putCacheList (line 189) during
            // AppCompat's first drawable load. Leaving mResources null forces
            // LoadedApk.getResources() to route through ResourcesManager on first
            // access, producing a properly-keyed Resources instance.

            // 9. Register in mPackages
            val putMethod = mPackages.javaClass.getMethod("put", Any::class.java, Any::class.java)
            putMethod.invoke(mPackages, appEntry.packageName, WeakReference(virtualLoadedApk))
            Timber.i("Registered LoadedApk for: ${appEntry.packageName}")

            // 10. Also register in mResourcePackages
            val mResourcePkgsField = ReflectUtils.getField(atClass, "mResourcePackages")
            if (mResourcePkgsField != null) {
                val mResourcePkgs = mResourcePkgsField.get(activityThread)
                if (mResourcePkgs != null) {
                    val resPutMethod = mResourcePkgs.javaClass.getMethod("put", Any::class.java, Any::class.java)
                    resPutMethod.invoke(mResourcePkgs, appEntry.packageName, WeakReference(virtualLoadedApk))
                }
            }

            registeredPackages.add(appEntry.packageName)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to register LoadedApk for: ${appEntry.packageName}")
            false
        }
    }
}
