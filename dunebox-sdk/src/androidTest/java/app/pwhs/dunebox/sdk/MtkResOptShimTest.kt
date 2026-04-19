package app.pwhs.dunebox.sdk

import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pwhs.dunebox.sdk.internal.hook.MtkResOptShim
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Verifies [MtkResOptShim] prevents the NPE at
 * `com.mediatek.res.AsyncDrawableCache.putCacheList:189`.
 *
 * The test directly invokes `AsyncDrawableCache.putCacheList(long, Drawable, int, Context)`
 * — the same call the vendor-patched `ResourcesImpl.cacheDrawable` makes — and
 * asserts it does not throw after the shim is applied.
 *
 * Skipped silently on non-MediaTek devices.
 */
@RunWith(AndroidJUnit4::class)
class MtkResOptShimTest {

    private val tag = "DuneboxShimTest"

    @Before
    fun bypass() {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    @Test
    fun shimPreventsPutCacheListNpe() {
        val cacheCls = try {
            Class.forName("com.mediatek.res.AsyncDrawableCache")
        } catch (e: ClassNotFoundException) {
            Log.i(tag, "Not a MediaTek device — skipping")
            return
        }

        // Sanity: singleton reachable
        val getInstance = cacheCls.getDeclaredMethod("getInstance").apply { isAccessible = true }
        val singleton = getInstance.invoke(null)
        assertNotEquals("getInstance() returned null", null, singleton)

        // Apply shim
        MtkResOptShim.apply(tag)

        // Reproduce the exact call ResourcesImpl.cacheDrawable makes into the
        // vendor cache. Signature from MtkDiagnosticTest output:
        //   void putCacheList(long key, Drawable drawable, int density, Context context)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val method = cacheCls.getDeclaredMethod(
            "putCacheList",
            java.lang.Long.TYPE,
            android.graphics.drawable.Drawable::class.java,
            java.lang.Integer.TYPE,
            android.content.Context::class.java,
        ).apply { isAccessible = true }

        val drawable = ColorDrawable(0xFF000000.toInt())
        val crashingKey = 0x7f08011aL
        val density = 480

        try {
            method.invoke(singleton, crashingKey, drawable, density, ctx)
            Log.i(tag, "putCacheList returned normally after shim ✓")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException
            throw AssertionError(
                "putCacheList still threw after shim: ${cause.javaClass.name}: ${cause.message}",
                cause,
            )
        }
    }
}
