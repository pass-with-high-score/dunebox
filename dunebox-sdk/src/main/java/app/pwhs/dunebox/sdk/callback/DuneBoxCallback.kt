package app.pwhs.dunebox.sdk.callback

/**
 * Callback interface for DuneBox SDK events.
 * All methods have default empty implementations so consumers
 * only need to override the ones they care about.
 *
 * For Kotlin consumers, prefer using `DuneBox.events` SharedFlow instead.
 *
 * Usage (Java):
 * ```java
 * DuneBox.INSTANCE.registerCallback(new DuneBoxCallback() {
 *     @Override
 *     public void onAppInstalled(@NotNull String packageName, int userId) {
 *         // handle
 *     }
 * });
 * ```
 */
interface DuneBoxCallback {
    fun onAppInstalled(packageName: String, userId: Int) {}
    fun onAppUninstalled(packageName: String, userId: Int) {}
    fun onAppStarted(packageName: String, userId: Int, pid: Int) {}
    fun onAppStopped(packageName: String, userId: Int) {}
    fun onAppCrashed(packageName: String, userId: Int, error: Throwable) {}
}
