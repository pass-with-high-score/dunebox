package app.pwhs.dunebox.sdk.internal.hook

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.pwhs.dunebox.sdk.internal.engine.StubActivityManager
import app.pwhs.dunebox.sdk.internal.engine.VirtualAppRegistry
import app.pwhs.dunebox.sdk.internal.loader.DexLoader
import timber.log.Timber

/**
 * Hooks ActivityThread.mInstrumentation to intercept Activity lifecycle.
 *
 * Since MIUI may not call newActivity() in some cases, HCallbackHook + LoadedApkHook
 * provide the primary launch mechanism. This Instrumentation hook serves as:
 * 1. A fallback for newActivity() — uses virtual ClassLoader to load real Activity
 * 2. Lifecycle delegation to the original Instrumentation
 */
internal object HookInstrumentation {

    private var originalInstrumentation: Instrumentation? = null
    private var isHooked = false
    private var hostContext: Context? = null
    private var dexLoader: DexLoader? = null

    fun hook(context: Context): Boolean {
        if (isHooked) {
            Timber.w("Instrumentation already hooked")
            return true
        }

        hostContext = context
        dexLoader = DexLoader(context)

        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentATMethod = ReflectUtils.getMethod(atClass, "currentActivityThread")
                ?: throw RuntimeException("Cannot find currentActivityThread()")
            val activityThread = currentATMethod.invoke(null)
                ?: throw RuntimeException("currentActivityThread() returned null")

            val instrField = ReflectUtils.getField(atClass, "mInstrumentation")
                ?: throw RuntimeException("Cannot find mInstrumentation field")

            originalInstrumentation = instrField.get(activityThread) as? Instrumentation
                ?: throw RuntimeException("mInstrumentation is null")

            val hookedInstrumentation = DuneBoxInstrumentation(originalInstrumentation!!)

            // CRITICAL: Copy mThread from original Instrumentation
            // Without this, the framework logs "Uninitialized ActivityThread"
            // and AppComponentFactory won't work properly
            try {
                val mThreadField = Instrumentation::class.java.getDeclaredField("mThread")
                mThreadField.isAccessible = true
                val thread = mThreadField.get(originalInstrumentation)
                mThreadField.set(hookedInstrumentation, thread)
                Timber.d("Copied mThread to hooked Instrumentation")
            } catch (e: Exception) {
                Timber.w(e, "Failed to copy mThread (non-fatal)")
            }

            instrField.set(activityThread, hookedInstrumentation)

            isHooked = true
            Timber.i("Instrumentation hook installed successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to hook Instrumentation")
            false
        }
    }

    fun getDexLoader(): DexLoader? = dexLoader

    /**
     * Custom Instrumentation.
     * Primary role: swap Activity class in newActivity() using virtual ClassLoader.
     * All other methods delegate to the original Instrumentation.
     */
    private class DuneBoxInstrumentation(
        private val base: Instrumentation
    ) : Instrumentation() {

        override fun newActivity(
            cl: ClassLoader,
            className: String,
            intent: Intent?
        ): Activity {
            Timber.d("newActivity called: className=$className")

            // Check if this is a virtual app Activity launch
            val virtualPkg = StubActivityManager.extractVirtualPackage(intent)

            if (virtualPkg != null) {
                // Determine the real target class
                val realTarget = if (StubActivityManager.isStubActivity(className)) {
                    StubActivityManager.extractRealTarget(intent) ?: className
                } else {
                    className
                }

                Timber.d("Virtual app: pkg=$virtualPkg, target=$realTarget")

                val appEntry = VirtualAppRegistry.getApp(virtualPkg)
                if (appEntry?.classLoader != null) {
                    try {
                        val realClass = appEntry.classLoader!!.loadClass(realTarget)
                        val activity = realClass.getDeclaredConstructor().newInstance() as Activity
                        Timber.i("✅ newActivity created: $realTarget via virtual ClassLoader")
                        return activity
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create virtual Activity: $realTarget")
                    }
                } else {
                    Timber.e("No ClassLoader for: $virtualPkg (entry=${appEntry != null})")
                }
            }

            return base.newActivity(cl, className, intent)
        }

        /**
         * callActivityOnCreate — NO LONGER injects VirtualContext.
         * LoadedApkHook now handles ClassLoader/Resources via the registered LoadedApk.
         * The framework creates the proper Context automatically.
         */
        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            Timber.d("callActivityOnCreate: ${activity.javaClass.name}")
            base.callActivityOnCreate(activity, icicle)
        }

        override fun callActivityOnCreate(
            activity: Activity,
            icicle: Bundle?,
            persistentState: android.os.PersistableBundle?
        ) {
            Timber.d("callActivityOnCreate (persistent): ${activity.javaClass.name}")
            base.callActivityOnCreate(activity, icicle, persistentState)
        }

        // Delegate all lifecycle methods to original
        override fun onCreate(arguments: Bundle?) = base.onCreate(arguments)
        override fun onStart() = base.onStart()
        override fun onDestroy() = base.onDestroy()
        override fun callActivityOnDestroy(activity: Activity) = base.callActivityOnDestroy(activity)
        override fun callActivityOnRestart(activity: Activity) = base.callActivityOnRestart(activity)
        override fun callActivityOnStart(activity: Activity) = base.callActivityOnStart(activity)
        override fun callActivityOnStop(activity: Activity) = base.callActivityOnStop(activity)
        override fun callActivityOnResume(activity: Activity) = base.callActivityOnResume(activity)
        override fun callActivityOnPause(activity: Activity) = base.callActivityOnPause(activity)
        override fun callActivityOnNewIntent(activity: Activity, intent: Intent?) = base.callActivityOnNewIntent(activity, intent)
        override fun callActivityOnPostCreate(activity: Activity, savedInstanceState: Bundle?) = base.callActivityOnPostCreate(activity, savedInstanceState)
        override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle) = base.callActivityOnSaveInstanceState(activity, outState)
    }
}
