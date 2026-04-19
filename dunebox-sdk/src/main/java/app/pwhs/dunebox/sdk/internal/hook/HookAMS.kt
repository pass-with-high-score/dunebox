package app.pwhs.dunebox.sdk.internal.hook

import android.os.Build
import timber.log.Timber
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Hooks the ActivityManagerService via Dynamic Proxy on IActivityManager.
 *
 * This intercepts key AMS calls:
 * - startActivity: Redirect to StubActivity
 * - startService: Redirect to StubService
 * - broadcastIntent: Filter broadcasts
 *
 * This is the core mechanism that allows virtual apps to launch their
 * Activities through DuneBox's pre-declared Stub components.
 */
internal class HookAMS {

    companion object {
        private const val TAG = "HookAMS"
        private var originalAMS: Any? = null
        private var isHooked = false

        /**
         * Install the AMS hook by replacing the IActivityManager singleton with a Dynamic Proxy.
         */
        fun hook(): Boolean {
            if (isHooked) {
                Timber.d("AMS already hooked, skipping")
                return true
            }

            return try {
                // Step 1: Get the IActivityManager singleton
                // On Android 8.0+ (API 26): ActivityManager.getService() returns IActivityManager
                val amClass = Class.forName("android.app.ActivityManager")
                val getServiceMethod = ReflectUtils.getMethod(amClass, "getService")
                    ?: throw RuntimeException("Cannot find ActivityManager.getService()")

                // The singleton is stored in ActivityManager.IActivityManagerSingleton
                val singletonField = ReflectUtils.getField(amClass, "IActivityManagerSingleton")
                    ?: throw RuntimeException("Cannot find IActivityManagerSingleton field")

                val singleton = singletonField.get(null)
                    ?: throw RuntimeException("IActivityManagerSingleton is null")

                // Step 2: Get the actual IActivityManager instance from the Singleton
                val singletonClass = Class.forName("android.util.Singleton")
                val instanceField = ReflectUtils.getField(singletonClass, "mInstance")
                    ?: throw RuntimeException("Cannot find Singleton.mInstance")

                val originalInstance = getServiceMethod.invoke(null)
                    ?: throw RuntimeException("getService() returned null")

                originalAMS = originalInstance

                // Step 3: Get the IActivityManager interface
                val iamInterface = Class.forName("android.app.IActivityManager")

                // Step 4: Create Dynamic Proxy
                val proxy = Proxy.newProxyInstance(
                    iamInterface.classLoader,
                    arrayOf(iamInterface),
                    AMSInvocationHandler(originalInstance)
                )

                // Step 5: Replace the singleton instance
                instanceField.set(singleton, proxy)

                isHooked = true
                Timber.i("AMS hook installed successfully")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to hook AMS")
                false
            }
        }

        /**
         * Get the original (unhooked) AMS instance.
         */
        fun getOriginal(): Any? = originalAMS
    }

    /**
     * InvocationHandler that intercepts IActivityManager method calls.
     * For MVP: logs all calls and passes through to the real AMS.
     * TODO: Implement actual Activity/Service redirection in Phase 3.
     */
    private class AMSInvocationHandler(
        private val original: Any
    ) : InvocationHandler {

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val methodName = method.name

            // Log intercepted calls for debugging
            when (methodName) {
                "startActivity" -> {
                    Timber.d("AMS.startActivity intercepted")
                    // TODO Phase 3: Check if this is a virtual app Activity
                    // If so, replace the Intent target with a StubActivity
                    // and store the real Intent in extras
                }
                "startService" -> {
                    Timber.d("AMS.startService intercepted")
                    // TODO Phase 3: Similar redirect for Services
                }
                "broadcastIntent", "broadcastIntentWithFeature" -> {
                    Timber.d("AMS.broadcastIntent intercepted")
                    // TODO Phase 3: Filter/redirect broadcasts
                }
                "getContentProvider" -> {
                    Timber.d("AMS.getContentProvider intercepted")
                    // TODO Phase 3: Return virtual ContentProvider
                }
            }

            // Pass through to original AMS
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
