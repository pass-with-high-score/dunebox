package app.pwhs.dunebox.stub

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Base stub Activity. All stub variants extend this.
 * The DuneBox engine hooks into the Activity lifecycle to replace
 * the stub with the actual virtual app's Activity at runtime.
 *
 * If you see this Activity's onCreate log, it means the Instrumentation
 * hook did NOT intercept — the real Activity was not swapped in.
 */
open class StubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug: log all extras to understand what happened
        val extras = intent?.extras
        Log.w("StubActivity", "=== StubActivity.onCreate() reached ===")
        Log.w("StubActivity", "Class: ${javaClass.name}")
        Log.w("StubActivity", "Real target: ${extras?.getString("_dunebox_real_target")}")
        Log.w("StubActivity", "Virtual pkg: ${extras?.getString("_dunebox_virtual_pkg")}")
        Log.w("StubActivity", "Intent: $intent")

        // If we reach here, the hook didn't swap us. Show a debug message.
        // In production this should never happen.
    }

    // ============================
    // Process :p0 — Standard
    // ============================
    class P0 {
        class S0 : StubActivity()
        class S1 : StubActivity()
        class S2 : StubActivity()
        class S3 : StubActivity()
        class S4 : StubActivity()

        // SingleTask
        class T0 : StubActivity()
        class T1 : StubActivity()
        class T2 : StubActivity()

        // SingleTop
        class Top0 : StubActivity()
        class Top1 : StubActivity()
    }

    // ============================
    // Process :p1 — Standard
    // ============================
    class P1 {
        class S0 : StubActivity()
        class S1 : StubActivity()
        class S2 : StubActivity()
    }
}
