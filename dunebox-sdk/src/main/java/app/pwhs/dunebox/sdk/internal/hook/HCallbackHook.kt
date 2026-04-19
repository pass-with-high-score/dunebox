package app.pwhs.dunebox.sdk.internal.hook

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Message
import app.pwhs.dunebox.sdk.internal.engine.StubActivityManager
import app.pwhs.dunebox.sdk.internal.engine.VirtualAppRegistry
import timber.log.Timber

/**
 * Hook ActivityThread.mH.mCallback to intercept Activity launch messages.
 *
 * Now that LoadedApkHook registers a proper LoadedApk with DexClassLoader
 * in ActivityThread.mPackages, we CAN safely swap the component name.
 * The framework will find our LoadedApk and use its ClassLoader.
 *
 * Flow:
 * 1. LoadedApkHook registers LoadedApk for virtual package (with DexClassLoader)
 * 2. HCallbackHook swaps StubActivity → real Activity in launch message
 * 3. Framework calls getPackageInfoNoCheck → finds our LoadedApk
 * 4. Uses DexClassLoader to load the real Activity class
 * 5. Activity is created successfully!
 */
internal object HCallbackHook {

    private var originalCallback: Handler.Callback? = null
    private var isHooked = false

    private var EXECUTE_TRANSACTION: Int = 159
    private var LAUNCH_ACTIVITY: Int = 100

    fun hook(): Boolean {
        if (isHooked) return true

        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentATMethod = ReflectUtils.getMethod(atClass, "currentActivityThread")
                ?: throw RuntimeException("Cannot find currentActivityThread()")
            val activityThread = currentATMethod.invoke(null)
                ?: throw RuntimeException("currentActivityThread() returned null")

            val mHField = ReflectUtils.getField(atClass, "mH")
                ?: throw RuntimeException("Cannot find mH field")
            val mH = mHField.get(activityThread) as? Handler
                ?: throw RuntimeException("mH is null")

            try {
                val hClass = Class.forName("android.app.ActivityThread\$H")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val execField = ReflectUtils.getField(hClass, "EXECUTE_TRANSACTION")
                    if (execField != null) EXECUTE_TRANSACTION = execField.getInt(null)
                }
                val launchField = ReflectUtils.getField(hClass, "LAUNCH_ACTIVITY")
                if (launchField != null) LAUNCH_ACTIVITY = launchField.getInt(null)
            } catch (_: Exception) {}

            val callbackField = ReflectUtils.getField(Handler::class.java, "mCallback")
                ?: throw RuntimeException("Cannot find Handler.mCallback")
            originalCallback = callbackField.get(mH) as? Handler.Callback
            callbackField.set(mH, ActivityThreadCallback(originalCallback))

            isHooked = true
            Timber.i("HCallback hook installed successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to hook HCallback")
            false
        }
    }

    private class ActivityThreadCallback(
        private val original: Handler.Callback?
    ) : Handler.Callback {

        override fun handleMessage(msg: Message): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (msg.what == EXECUTE_TRANSACTION) {
                    try { handleExecuteTransaction(msg.obj) }
                    catch (e: Exception) { Timber.e(e, "EXECUTE_TRANSACTION error") }
                }
            } else {
                if (msg.what == LAUNCH_ACTIVITY) {
                    try { handleLaunchActivityLegacy(msg.obj) }
                    catch (e: Exception) { Timber.e(e, "LAUNCH_ACTIVITY error") }
                }
            }
            return original?.handleMessage(msg) ?: false
        }

        private fun handleExecuteTransaction(transaction: Any?) {
            if (transaction == null) return
            val callbacksField = ReflectUtils.getField(transaction.javaClass, "mActivityCallbacks") ?: return

            @Suppress("UNCHECKED_CAST")
            val callbacks = callbacksField.get(transaction) as? List<Any> ?: return

            for (callback in callbacks) {
                if (callback.javaClass.name.contains("LaunchActivityItem")) {
                    handleLaunchActivityItem(callback)
                    return
                }
            }
        }

        private fun handleLaunchActivityItem(launchItem: Any) {
            val intentField = ReflectUtils.getField(launchItem.javaClass, "mIntent") ?: return
            val intent = intentField.get(launchItem) as? Intent ?: return

            val componentName = intent.component ?: return
            val stubClassName = componentName.className

            if (!StubActivityManager.isStubActivity(stubClassName)) return

            val virtualPkg = StubActivityManager.extractVirtualPackage(intent) ?: return
            val realTarget = StubActivityManager.extractRealTarget(intent) ?: return

            val appEntry = VirtualAppRegistry.getApp(virtualPkg) ?: run {
                Timber.e("App not registered: $virtualPkg")
                return
            }

            // Step 0: Register LoadedApk for this virtual package (if not done yet)
            val registered = LoadedApkHook.registerVirtualPackage(appEntry)
            if (!registered) {
                Timber.e("Failed to register LoadedApk for: $virtualPkg")
                return
            }

            Timber.i("🔄 Swapping launch: $stubClassName -> $realTarget ($virtualPkg)")

            // Step 1: Swap the Intent component to the REAL Activity
            intent.component = android.content.ComponentName(virtualPkg, realTarget)

            // Step 2: Set extras ClassLoader
            appEntry.classLoader?.let { intent.setExtrasClassLoader(it) }

            // Step 3: Update ActivityInfo in LaunchActivityItem
            val actInfoField = ReflectUtils.getField(launchItem.javaClass, "mInfo")
            if (actInfoField != null) {
                val activityInfo = actInfoField.get(launchItem) as? ActivityInfo
                if (activityInfo != null) {
                    // Create proper ApplicationInfo for the virtual app
                    val virtualAppInfo = ApplicationInfo()
                    virtualAppInfo.packageName = appEntry.packageName
                    virtualAppInfo.className = appEntry.parsedInfo.applicationClassName
                    virtualAppInfo.sourceDir = appEntry.apkPath
                    virtualAppInfo.publicSourceDir = appEntry.apkPath
                    virtualAppInfo.dataDir = appEntry.dataDir
                    virtualAppInfo.nativeLibraryDir = "${appEntry.dataDir}/lib"

                    // Copy uid/flags from host to avoid permission issues
                    try {
                        val atClass = Class.forName("android.app.ActivityThread")
                        val currentAppMethod = atClass.getDeclaredMethod("currentApplication")
                        currentAppMethod.isAccessible = true
                        val app = currentAppMethod.invoke(null) as? android.app.Application
                        val hostInfo = app?.applicationInfo
                        if (hostInfo != null) {
                            virtualAppInfo.uid = hostInfo.uid
                            virtualAppInfo.targetSdkVersion = hostInfo.targetSdkVersion
                            virtualAppInfo.flags = hostInfo.flags
                        }
                    } catch (_: Exception) {}

                    activityInfo.applicationInfo = virtualAppInfo
                    activityInfo.packageName = virtualPkg
                    activityInfo.name = realTarget
                    activityInfo.theme = android.R.style.Theme_Material_Light_DarkActionBar
                    Timber.d("ActivityInfo updated: $virtualPkg/$realTarget")
                }
            }

            Timber.i("✅ Launch swap complete: $realTarget")
        }

        private fun handleLaunchActivityLegacy(record: Any?) {
            if (record == null) return
            val intentField = ReflectUtils.getField(record.javaClass, "intent")
            val intent = intentField?.get(record) as? Intent ?: return
            val componentName = intent.component ?: return
            if (!StubActivityManager.isStubActivity(componentName.className)) return

            val virtualPkg = StubActivityManager.extractVirtualPackage(intent) ?: return
            val realTarget = StubActivityManager.extractRealTarget(intent) ?: return
            val appEntry = VirtualAppRegistry.getApp(virtualPkg) ?: return

            LoadedApkHook.registerVirtualPackage(appEntry)

            intent.component = android.content.ComponentName(virtualPkg, realTarget)
            appEntry.classLoader?.let { intent.setExtrasClassLoader(it) }

            val actInfoField = ReflectUtils.getField(record.javaClass, "activityInfo")
            val activityInfo = actInfoField?.get(record) as? ActivityInfo
            if (activityInfo != null) {
                activityInfo.name = realTarget
                activityInfo.packageName = virtualPkg
                activityInfo.theme = android.R.style.Theme_Material_Light_DarkActionBar
            }
            Timber.i("✅ Legacy swap: $realTarget")
        }
    }
}
