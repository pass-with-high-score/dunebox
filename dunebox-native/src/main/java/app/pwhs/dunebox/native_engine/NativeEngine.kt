package app.pwhs.dunebox.native_engine

import timber.log.Timber

/**
 * Kotlin wrapper for the native IO redirect engine.
 * Provides type-safe access to libc hook functions via JNI.
 */
object NativeEngine {

    private var isLoaded = false

    /**
     * Initialize the native engine. Must be called before any other methods.
     */
    fun init() {
        if (isLoaded) return
        try {
            System.loadLibrary("dunebox-native")
            nativeInit()
            isLoaded = true
            Timber.d("NativeEngine initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native library")
            throw RuntimeException("DuneBox native engine failed to initialize", e)
        }
    }

    /**
     * Add a path redirect rule.
     * When the virtual app accesses [fromPath], it will be transparently redirected to [toPath].
     *
     * Example:
     * ```
     * addRedirectRule("/data/data/com.example.app", "/data/data/app.pwhs.dunebox/virtual/0/com.example.app")
     * ```
     */
    fun addRedirectRule(fromPath: String, toPath: String) {
        ensureLoaded()
        nativeAddRedirectRule(fromPath, toPath)
        Timber.d("Added redirect rule: $fromPath -> $toPath")
    }

    /**
     * Add a path deny rule.
     * When the virtual app tries to access [path], the operation will fail with EACCES.
     *
     * Example:
     * ```
     * addDenyRule("/proc/self/maps")
     * ```
     */
    fun addDenyRule(path: String) {
        ensureLoaded()
        nativeAddDenyRule(path)
        Timber.d("Added deny rule: $path")
    }

    /**
     * Start IO redirect. Installs Dobby hooks on libc functions.
     * Must be called after all rules have been added.
     *
     * @return true if hooks were installed successfully
     */
    fun startRedirect(): Boolean {
        ensureLoaded()
        val result = nativeStartRedirect()
        if (result) {
            Timber.i("IO redirect started successfully")
        } else {
            Timber.e("Failed to start IO redirect")
        }
        return result
    }

    /**
     * Stop IO redirect. Clears all rules (hooks remain in place but become no-ops).
     */
    fun stopRedirect() {
        ensureLoaded()
        nativeStopRedirect()
        Timber.i("IO redirect stopped")
    }

    /**
     * Clear all rules without stopping redirect.
     */
    fun clearRules() {
        ensureLoaded()
        nativeClearRules()
    }

    private fun ensureLoaded() {
        check(isLoaded) { "NativeEngine not initialized. Call init() first." }
    }

    // ========================
    // JNI Native Methods
    // ========================
    private external fun nativeInit()
    private external fun nativeAddRedirectRule(fromPath: String, toPath: String)
    private external fun nativeAddDenyRule(path: String)
    private external fun nativeStartRedirect(): Boolean
    private external fun nativeStopRedirect()
    private external fun nativeClearRules()
}
