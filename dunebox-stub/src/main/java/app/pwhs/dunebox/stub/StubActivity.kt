package app.pwhs.dunebox.stub

import android.app.Activity
import android.os.Bundle

/**
 * Base stub Activity. All stub variants extend this.
 * The DuneBox engine hooks into the Activity lifecycle to replace
 * the stub with the actual virtual app's Activity at runtime.
 */
open class StubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The BinderHookManager / HookInstrumentation will intercept this
        // and replace with the real Activity from the virtual app.
        // If we reach here without hooking, something went wrong.
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
