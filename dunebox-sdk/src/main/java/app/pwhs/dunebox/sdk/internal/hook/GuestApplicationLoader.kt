package app.pwhs.dunebox.sdk.internal.hook

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import app.pwhs.dunebox.sdk.internal.engine.VirtualAppRegistry

/**
 * Instantiates and initializes the guest APK's `<application android:name>` class
 * in the host process so Dagger / Hilt / custom Application.onCreate logic runs
 * before any guest Activity is launched.
 *
 * After load, the guest Application becomes the one returned by:
 *   - `Activity.getApplication()` (via patched `Activity.mApplication`)
 *   - `ActivityThread.currentApplication()` (via `mInitialApplication`)
 *   - `Context.getApplicationContext()` (via `LoadedApk.mApplication`)
 *
 * so Hilt's `ActivityComponentManager` / Dagger component factories resolve
 * the guest Application cast successfully.
 */
internal object GuestApplicationLoader {

    private const val TAG = "DuneboxApp"

    /**
     * Load the guest Application if not already loaded. Safe to call repeatedly.
     * Returns the loaded Application, or null on failure (caller should log +
     * fall back to host application).
     */
    @Synchronized
    fun loadIfNeeded(hostContext: Context, appEntry: VirtualAppRegistry.AppEntry): Application? {
        appEntry.application?.let { return it }

        val classLoader = appEntry.classLoader ?: run {
            Log.e(TAG, "No ClassLoader for ${appEntry.packageName}")
            return null
        }

        val appClassName = appEntry.parsedInfo.applicationClassName
            ?: "android.app.Application"

        return try {
            // 1. Load the class via guest ClassLoader
            val appClass = classLoader.loadClass(appClassName)
            Log.i(TAG, "Loading guest Application: $appClassName")

            // 2. Instantiate via default constructor
            val guestApp = appClass.getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance() as Application

            // 3. Build a Context to attach: create one that wraps host base
            //    but reports guest packageName / has guest Resources. The
            //    simplest source is Activity's base context, but during
            //    pre-activity init we reuse the LoadedApk that's keyed to
            //    the guest package.
            val guestContext = createGuestBaseContext(hostContext, appEntry)
                ?: hostContext.applicationContext

            // 4. Call Application.attach(Context) — package-private, via reflection
            val attachMethod = Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
            attachMethod.invoke(guestApp, guestContext)
            Log.i(TAG, "Application.attach() done")

            // 5. Publish on registry BEFORE onCreate — guest onCreate may
            //    synchronously query Activity.getApplication() / etc.
            appEntry.application = guestApp
            patchLoadedApkMApplication(appEntry.packageName, guestApp)
            patchInitialApplication(guestApp)

            // 6. Run guest Application.onCreate() — this is where Dagger/Hilt
            //    builds its component graph.
            guestApp.onCreate()
            Log.i(TAG, "Application.onCreate() done: $appClassName")

            guestApp
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load guest Application $appClassName", t)
            appEntry.application = null
            null
        }
    }

    /**
     * Returns a Context that:
     *  - has guest Resources (via our virtual LoadedApk keyed in mPackages)
     *  - reports guest package name
     *  - shares the host ActivityThread / process
     *
     * We build it by calling host's `createPackageContext(guestPkg,
     * CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY)`. Our LoadedApkHook has
     * already registered a virtual LoadedApk under guestPkg in
     * ActivityThread.mPackages, so this returns a ContextImpl backed by it
     * without needing a real system install.
     */
    private fun createGuestBaseContext(
        hostContext: Context,
        appEntry: VirtualAppRegistry.AppEntry,
    ): Context? {
        return try {
            hostContext.createPackageContext(
                appEntry.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "createPackageContext(${appEntry.packageName}) failed: $t — falling back to host context")
            null
        }
    }

    /**
     * Set `LoadedApk.mApplication` for the virtual LoadedApk so any path that
     * calls `LoadedApk.getApplication()` returns the guest instance.
     */
    private fun patchLoadedApkMApplication(packageName: String, guestApp: Application) {
        try {
            val at = Class.forName("android.app.ActivityThread")
            val currentAT = at.getMethod("currentActivityThread").invoke(null) ?: return
            val mPackagesField = at.getDeclaredField("mPackages").apply { isAccessible = true }
            val mPackages = mPackagesField.get(currentAT) as? Map<*, *> ?: return
            val ref = mPackages[packageName] ?: return
            val get = ref.javaClass.getMethod("get")
            val loadedApk = get.invoke(ref) ?: return
            val mAppField = loadedApk.javaClass.getDeclaredField("mApplication")
                .apply { isAccessible = true }
            mAppField.set(loadedApk, guestApp)
            Log.i(TAG, "LoadedApk[$packageName].mApplication <- guest")
        } catch (t: Throwable) {
            Log.w(TAG, "patchLoadedApkMApplication failed: $t")
        }
    }

    /**
     * Set `ActivityThread.mInitialApplication` so `currentApplication()` returns
     * guest. This is what `ActivityThread.currentApplication()` exposes; many
     * libraries (Hilt's ApplicationComponentManager, etc.) read it.
     */
    private fun patchInitialApplication(guestApp: Application) {
        try {
            val at = Class.forName("android.app.ActivityThread")
            val currentAT = at.getMethod("currentActivityThread").invoke(null) ?: return
            val field = at.getDeclaredField("mInitialApplication").apply { isAccessible = true }
            field.set(currentAT, guestApp)
            Log.i(TAG, "ActivityThread.mInitialApplication <- guest")
        } catch (t: Throwable) {
            Log.w(TAG, "patchInitialApplication failed: $t")
        }
    }

    /**
     * Point an Activity's `mApplication` at the guest Application so
     * `activity.getApplication()` returns it. Called per-Activity from
     * HookInstrumentation.callActivityOnCreate BEFORE super.onCreate runs,
     * so Hilt's generated `onContextAvailable` sees the right Application.
     */
    fun rebindActivityApplication(activity: android.app.Activity, guestApp: Application) {
        try {
            val f = android.app.Activity::class.java.getDeclaredField("mApplication")
                .apply { isAccessible = true }
            val existing = f.get(activity)
            if (existing !== guestApp) {
                f.set(activity, guestApp)
                Log.i(TAG, "Activity[${activity.javaClass.simpleName}].mApplication <- guest")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rebindActivityApplication failed: $t")
        }
    }
}
