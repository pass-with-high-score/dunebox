package app.pwhs.dunebox

import android.app.Application
import android.content.Context
import app.pwhs.dunebox.sdk.DuneBox
import app.pwhs.dunebox.sdk.config.LogLevel
import timber.log.Timber

/**
 * DuneBox Application class.
 * Initializes the DuneBox SDK in attachBaseContext — this is critical
 * because hooks must be installed before any other component initializes.
 */
class DuneBoxApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // Initialize DuneBox SDK — MUST be first
        DuneBox.init(this) {
            logLevel = LogLevel.DEBUG
            maxVirtualUsers = 3
            enableIORedirect = true
            enableBinderHook = true
        }

        // If this is a virtual client process, skip normal app init
        if (DuneBox.isClientProcess()) return

        // Normal app initialization can continue here
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
