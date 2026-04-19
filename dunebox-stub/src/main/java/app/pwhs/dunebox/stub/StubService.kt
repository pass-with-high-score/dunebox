package app.pwhs.dunebox.stub

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Base stub Service. The DuneBox engine intercepts service starts
 * and redirects them through these stubs.
 */
open class StubService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hooked by BinderHookManager to run virtual app's service logic
        return START_NOT_STICKY
    }

    // Process :p0
    class P0 {
        class S0 : StubService()
        class S1 : StubService()
        class S2 : StubService()
    }

    // Process :p1
    class P1 {
        class S0 : StubService()
        class S1 : StubService()
    }
}
