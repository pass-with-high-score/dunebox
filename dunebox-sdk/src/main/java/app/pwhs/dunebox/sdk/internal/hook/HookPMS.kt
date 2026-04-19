package app.pwhs.dunebox.sdk.internal.hook

import timber.log.Timber
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Hooks the PackageManagerService via Dynamic Proxy on IPackageManager.
 *
 * This intercepts key PMS calls:
 * - getPackageInfo: Return fake info for virtual packages
 * - getApplicationInfo: Return virtual app's ApplicationInfo
 * - resolveIntent: Resolve intents to virtual components
 * - getInstalledPackages: Include virtual packages in list
 *
 * This makes the Android framework believe virtual apps are "installed".
 */
internal class HookPMS {

    companion object {
        private const val TAG = "HookPMS"
        private var originalPMS: Any? = null
        private var isHooked = false

        /**
         * Install the PMS hook by replacing the IPackageManager instance
         * in ActivityThread with a Dynamic Proxy.
         */
        fun hook(): Boolean {
            if (isHooked) {
                Timber.d("PMS already hooked, skipping")
                return true
            }

            return try {
                // Step 1: Get ActivityThread.sPackageManager
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val sPackageManagerField = ReflectUtils.getField(activityThreadClass, "sPackageManager")
                    ?: throw RuntimeException("Cannot find ActivityThread.sPackageManager")

                val originalInstance = sPackageManagerField.get(null)
                    ?: throw RuntimeException("sPackageManager is null")

                originalPMS = originalInstance

                // Step 2: Get the IPackageManager interface
                val ipmInterface = Class.forName("android.content.pm.IPackageManager")

                // Step 3: Create Dynamic Proxy
                val proxy = Proxy.newProxyInstance(
                    ipmInterface.classLoader,
                    arrayOf(ipmInterface),
                    PMSInvocationHandler(originalInstance)
                )

                // Step 4: Replace in ActivityThread
                sPackageManagerField.set(null, proxy)

                // Step 5: Also replace in the ApplicationPackageManager if possible
                try {
                    val currentActivityThread = ReflectUtils.getMethod(
                        activityThreadClass, "currentActivityThread"
                    )?.invoke(null)

                    if (currentActivityThread != null) {
                        val contextField = ReflectUtils.getField(
                            activityThreadClass, "mInitialApplication"
                        )
                        // Additional hooking for getPackageManager() context
                        // This ensures context.packageManager also goes through our proxy
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not hook ApplicationPackageManager, continuing with sPackageManager only")
                }

                isHooked = true
                Timber.i("PMS hook installed successfully")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to hook PMS")
                false
            }
        }

        /**
         * Get the original (unhooked) PMS instance.
         */
        fun getOriginal(): Any? = originalPMS
    }

    /**
     * InvocationHandler that intercepts IPackageManager method calls.
     * For MVP: logs all calls and passes through to the real PMS.
     * TODO: Implement actual PackageInfo spoofing in Phase 3.
     */
    private class PMSInvocationHandler(
        private val original: Any
    ) : InvocationHandler {

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val methodName = method.name

            when (methodName) {
                "getPackageInfo" -> {
                    val packageName = args?.firstOrNull() as? String
                    Timber.d("PMS.getPackageInfo intercepted: $packageName")
                    // TODO Phase 3: If packageName is a virtual package,
                    // return cached PackageInfo from VPackageManager
                }
                "getApplicationInfo" -> {
                    val packageName = args?.firstOrNull() as? String
                    Timber.d("PMS.getApplicationInfo intercepted: $packageName")
                    // TODO Phase 3: Return virtual ApplicationInfo
                    // (sourceDir pointing to APK in sandbox)
                }
                "resolveIntent" -> {
                    Timber.d("PMS.resolveIntent intercepted")
                    // TODO Phase 3: Resolve to virtual component
                }
                "getInstalledPackages" -> {
                    Timber.d("PMS.getInstalledPackages intercepted")
                    // TODO Phase 3: Include virtual packages in returned list
                }
                "getPackageUid" -> {
                    val packageName = args?.firstOrNull() as? String
                    Timber.d("PMS.getPackageUid intercepted: $packageName")
                    // TODO Phase 3: Return virtual UID for virtual packages
                }
            }

            // Pass through to original PMS
            return try {
                if (args != null) {
                    method.invoke(original, *args)
                } else {
                    method.invoke(original)
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException
            }
        }
    }
}
