package app.pwhs.dunebox.sdk.config

import app.pwhs.dunebox.sdk.DuneBoxDsl

/**
 * Log level for DuneBox SDK.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
}

/**
 * Stub configuration preset.
 */
enum class StubConfig {
    /** Minimal stubs: 10 Activities, 3 Services, 2 Providers */
    MINIMAL,

    /** Default stubs: 20 Activities, 5 Services, 3 Providers */
    DEFAULT,

    /** Full stubs: 50 Activities, 10 Services, 5 Providers (for complex apps) */
    FULL
}

/**
 * Configuration for DuneBox SDK initialization.
 *
 * Usage:
 * ```
 * DuneBox.init(context) {
 *     logLevel = LogLevel.DEBUG
 *     maxVirtualUsers = 3
 *     enableIORedirect = true
 * }
 * ```
 */
@DuneBoxDsl
class DuneBoxConfig {
    /** Logging verbosity. Default: WARN */
    var logLevel: LogLevel = LogLevel.WARN

    /** Maximum number of virtual user spaces. Default: 3 */
    var maxVirtualUsers: Int = 3

    /** Stub component configuration. Default: DEFAULT */
    var stubConfig: StubConfig = StubConfig.DEFAULT

    /** Enable native IO redirect hooks. Default: true */
    var enableIORedirect: Boolean = true

    /** Enable Binder hooks (AMS/PMS). Default: true */
    var enableBinderHook: Boolean = true

    internal fun validate() {
        require(maxVirtualUsers in 1..10) {
            "maxVirtualUsers must be between 1 and 10, got $maxVirtualUsers"
        }
    }
}
