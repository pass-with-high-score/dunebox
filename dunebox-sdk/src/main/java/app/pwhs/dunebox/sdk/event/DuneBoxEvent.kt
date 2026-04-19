package app.pwhs.dunebox.sdk.event

/**
 * Sealed class representing all events emitted by the DuneBox SDK.
 * Collect these via `DuneBox.events` SharedFlow.
 *
 * Usage:
 * ```
 * DuneBox.events.collect { event ->
 *     when (event) {
 *         is DuneBoxEvent.AppInstalled -> { ... }
 *         is DuneBoxEvent.AppLaunched -> { ... }
 *         is DuneBoxEvent.AppCrashed -> { ... }
 *         ...
 *     }
 * }
 * ```
 */
sealed class DuneBoxEvent {

    /** Emitted when an app is successfully cloned into the sandbox */
    data class AppInstalled(
        val packageName: String,
        val userId: Int
    ) : DuneBoxEvent()

    /** Emitted when an app is removed from the sandbox */
    data class AppUninstalled(
        val packageName: String,
        val userId: Int
    ) : DuneBoxEvent()

    /** Emitted when a virtual app is launched */
    data class AppLaunched(
        val packageName: String,
        val userId: Int,
        val pid: Int
    ) : DuneBoxEvent()

    /** Emitted when a virtual app stops */
    data class AppStopped(
        val packageName: String,
        val userId: Int
    ) : DuneBoxEvent()

    /** Emitted when a virtual app crashes */
    data class AppCrashed(
        val packageName: String,
        val userId: Int,
        val error: Throwable
    ) : DuneBoxEvent()

    /** Emitted when SDK initialization completes */
    data class Initialized(
        val success: Boolean,
        val message: String = ""
    ) : DuneBoxEvent()
}
