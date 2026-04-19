package app.pwhs.dunebox.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * On-device probe of MIUI + MediaTek vendor classes that cause AppCompat's
 * `abc_vector_test` load to NPE in container mode (see LoadedApkHook.kt crash).
 *
 * Run with:
 *   ./gradlew :dunebox-sdk:connectedDebugAndroidTest
 *
 * Output: filter `adb logcat -s DuneboxDiag` (or run `./gradlew ... --info`).
 */
@RunWith(AndroidJUnit4::class)
class MtkDiagnosticTest {

    private val tag = "DuneboxDiag"

    @Before
    fun bypassHiddenApi() {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    @Test
    fun dumpMtkResOptSurface() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        Log.e(tag, "=== device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / ${android.os.Build.HARDWARE}")
        Log.e(tag, "=== MIUI-sdk/Android sdk: ${android.os.Build.VERSION.SDK_INT}")

        // 1. Walk the hierarchy of the live ResourcesImpl to confirm no ResOpt field
        dumpResourcesImplHierarchy(ctx.resources)

        // 2. Dump each MediaTek vendor class structure + singleton field values
        for (name in listOf(
            "com.mediatek.res.ResOptExtImpl",
            "com.mediatek.res.AsyncDrawableCache",
            "android.content.res.ResourcesImpl",
            "android.content.res.MiuiResourcesImpl",
        )) {
            dumpClass(name)
        }
    }

    private fun dumpResourcesImplHierarchy(resources: android.content.res.Resources) {
        val implField = android.content.res.Resources::class.java
            .getDeclaredField("mResourcesImpl")
            .apply { isAccessible = true }
        val impl = implField.get(resources) ?: run {
            Log.e(tag, "Resources.mResourcesImpl is null"); return
        }
        Log.e(tag, "--- Live Resources.mResourcesImpl = ${impl.javaClass.name}")
        var c: Class<*>? = impl.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                if (f.type.name.contains("ResOpt", ignoreCase = true)) {
                    f.isAccessible = true
                    val v = runCatching { f.get(impl) }.getOrNull()
                    Log.e(tag, "  FOUND ResOpt field: ${c.simpleName}.${f.name} type=${f.type.name} value=$v")
                }
            }
            c = c.superclass
        }
    }

    private fun dumpClass(fqn: String) {
        Log.e(tag, "--- $fqn")
        val cls = try {
            Class.forName(fqn)
        } catch (e: ClassNotFoundException) {
            Log.e(tag, "  (not present on this device)")
            return
        } catch (e: Throwable) {
            Log.e(tag, "  load failed: $e"); return
        }

        Log.e(tag, "  superclass: ${cls.superclass?.name}")
        Log.e(tag, "  interfaces: ${cls.interfaces.joinToString { it.name }}")

        val staticFields = cls.declaredFields.filter { Modifier.isStatic(it.modifiers) }
        val instanceFields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }

        Log.e(tag, "  static fields (${staticFields.size}):")
        for (f in staticFields) dumpField(null, f, cls)

        Log.e(tag, "  instance fields (${instanceFields.size}):")
        for (f in instanceFields) {
            Log.e(tag, "    ${Modifier.toString(f.modifiers)} ${f.type.simpleName} ${f.name}")
        }

        Log.e(tag, "  methods:")
        for (m in cls.declaredMethods.sortedBy { it.name }) {
            val paramTypes = m.parameterTypes.joinToString { it.simpleName }
            Log.e(tag, "    ${m.returnType.simpleName} ${m.name}($paramTypes)")
        }

        // Try to get a live singleton and introspect it
        val singleton = findSingleton(cls)
        if (singleton != null) {
            Log.e(tag, "  >> singleton instance obtained: ${singleton.javaClass.name}")
            dumpInstanceFieldValues(singleton)
        } else {
            Log.e(tag, "  >> no singleton found (no self-typed static field, no getInstance())")
        }
    }

    private fun dumpField(target: Any?, f: Field, declaring: Class<*>) {
        f.isAccessible = true
        val v = runCatching { f.get(target) }.getOrNull()
        val prefix = if (target == null) "    " else "      "
        Log.e(tag, "$prefix${Modifier.toString(f.modifiers)} ${f.type.simpleName} ${f.name} = ${safeToString(v)}")
    }

    private fun dumpInstanceFieldValues(obj: Any) {
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                f.isAccessible = true
                val v = runCatching { f.get(obj) }.getOrNull()
                Log.e(tag, "      ${c!!.simpleName}.${f.name} = ${safeToString(v)}")
            }
            c = c.superclass
        }
    }

    private fun findSingleton(cls: Class<*>): Any? {
        for (f in cls.declaredFields) {
            if (!Modifier.isStatic(f.modifiers)) continue
            if (!cls.isAssignableFrom(f.type)) continue
            try {
                f.isAccessible = true
                val v = f.get(null)
                if (v != null) return v
            } catch (_: Throwable) {}
        }
        return try {
            val m = cls.getDeclaredMethod("getInstance")
            m.isAccessible = true
            m.invoke(null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeToString(v: Any?): String {
        if (v == null) return "null"
        return try {
            val s = v.toString()
            if (s.length > 200) s.substring(0, 200) + "…(${s.length})" else s
        } catch (e: Throwable) {
            "<toString threw: ${e.javaClass.simpleName}>"
        }
    }
}
