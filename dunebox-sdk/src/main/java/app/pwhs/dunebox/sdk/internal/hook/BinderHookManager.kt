package app.pwhs.dunebox.sdk.internal.hook

import android.content.Context
import app.pwhs.dunebox.sdk.internal.engine.StubActivityManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

/**
 * Orchestrator that initializes all hooks in the correct order.
 * Called from DuneBox.init() during Application.attachBaseContext().
 *
 * Hook order matters:
 * 1. Hidden API bypass (must be first)
 * 2. AMS hook (Activity/Service interception)
 * 3. PMS hook (Package info spoofing)
 * 4. Instrumentation hook (Activity lifecycle — swaps stubs with real Activities)
 */
internal object BinderHookManager {

    private var isInitialized = false

    /**
     * Initialize all hooks. Must be called from Application.attachBaseContext().
     *
     * @param context Application context
     * @param enableBinderHook Whether to install AMS/PMS hooks
     */
    fun init(context: Context, enableBinderHook: Boolean = true) {
        if (isInitialized) {
            Timber.w("BinderHookManager already initialized, skipping")
            return
        }

        Timber.i("Initializing BinderHookManager...")

        // Step 0: Initialize StubActivityManager
        StubActivityManager.init(context.packageName)

        // Step 1: Bypass hidden API restrictions (Android 9+)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.addHiddenApiExemptions("")  // Exempt all
                Timber.d("Hidden API bypass installed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to bypass hidden API restrictions")
            // Non-fatal: some hooks might still work
        }

        // Step 1b: Neutralize MIUI+MediaTek AsyncDrawableCache — its vendor-patched
        // `ResourcesImpl.cacheDrawable` NPE's for non-host Resources (guest apps
        // launched in-container). See MtkResOptShim.kt for the full story.
        MtkResOptShim.apply()

        if (!enableBinderHook) {
            Timber.i("Binder hooks disabled by config")
            isInitialized = true
            return
        }

        // Step 2: Hook AMS
        val amsResult = HookAMS.hook()
        Timber.i("AMS hook: ${if (amsResult) "SUCCESS" else "FAILED"}")

        // Step 3: Hook PMS
        val pmsResult = HookPMS.hook()
        Timber.i("PMS hook: ${if (pmsResult) "SUCCESS" else "FAILED"}")

        // Step 4: Hook Instrumentation (VirtualContext injection in callActivityOnCreate)
        val instrResult = HookInstrumentation.hook(context)
        Timber.i("Instrumentation hook: ${if (instrResult) "SUCCESS" else "FAILED"}")

        // Step 5: Hook ActivityThread.mH.mCallback (the CRITICAL piece!)
        // This intercepts EXECUTE_TRANSACTION / LAUNCH_ACTIVITY messages
        // and swaps StubActivity → real Activity info BEFORE the framework
        // creates the Activity instance. This is how BlackBox/VirtualApp do it.
        val hCallbackResult = HCallbackHook.hook()
        Timber.i("HCallback hook: ${if (hCallbackResult) "SUCCESS" else "FAILED"}")

        isInitialized = true
        Timber.i("BinderHookManager initialized (AMS=$amsResult, PMS=$pmsResult, Instr=$instrResult, HCallback=$hCallbackResult)")
    }

    /**
     * Check if hooks are initialized.
     */
    fun isInitialized(): Boolean = isInitialized
}
