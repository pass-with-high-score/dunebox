package app.pwhs.dunebox.sdk.internal.hook

import android.util.Log
import java.lang.reflect.Modifier

/**
 * Neutralizes MIUI + MediaTek's `com.mediatek.res.AsyncDrawableCache` so its
 * `putCacheList(long, Drawable, int, Context)` no longer NPEs when called from
 * `ResourcesImpl.cacheDrawable` for a non-host Resources.
 *
 * Diagnostic output (MtkDiagnosticTest) showed:
 *   - `AsyncDrawableCache` state is entirely static
 *   - `sFeatureConfig` ("1") gates the feature in `isEnableFeature()`
 *   - `sPreloadList` is a hard-coded whitelist of 2 packages
 *   - No state is per-Resources; one process-wide scrub is sufficient
 *
 * Strategy: null-out / empty every static Map/List/Set/Array, blank out the
 * feature-config string, and flip `isPreloaded` to true — this forces every
 * guard in `putCacheList` to short-circuit before reaching the problematic
 * `.equals()` on line 189.
 */
internal object MtkResOptShim {

    private var applied = false

    fun apply(tag: String = "DuneboxMtk"): Boolean {
        if (applied) return true

        val cache = tryLoad("com.mediatek.res.AsyncDrawableCache", tag)
        val ext = tryLoad("com.mediatek.res.ResOptExtImpl", tag)
        if (cache == null && ext == null) {
            Log.i(tag, "No MediaTek ResOpt classes present — device does not need shim")
            applied = true
            return true
        }

        cache?.let { neutralize(it, tag) }
        ext?.let { neutralize(it, tag) }

        applied = true
        Log.i(tag, "MTK ResOpt shim applied")
        return true
    }

    private fun tryLoad(fqn: String, tag: String): Class<*>? {
        val loaders = listOfNotNull(
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader(),
            MtkResOptShim::class.java.classLoader,
        )
        for (cl in loaders) {
            try {
                return Class.forName(fqn, true, cl)
            } catch (_: Throwable) {
            }
        }
        try {
            return Class.forName(fqn)
        } catch (e: ClassNotFoundException) {
            return null
        } catch (e: Throwable) {
            Log.w(tag, "Failed to load $fqn: $e")
            return null
        }
    }

    private fun neutralize(cls: Class<*>, tag: String) {
        for (f in cls.declaredFields) {
            if (!Modifier.isStatic(f.modifiers)) continue
            if (Modifier.isFinal(f.modifiers) && !isCollectionType(f.type)) {
                // Skip true constants (final non-mutable scalars)
                continue
            }
            try {
                f.isAccessible = true
                val newValue = replacementFor(f.name, f.type)
                if (newValue === SKIP) continue
                f.set(null, newValue)
                Log.i(tag, "  ${cls.simpleName}.${f.name} <- ${describe(newValue)}")
            } catch (t: Throwable) {
                Log.w(tag, "  skip ${cls.simpleName}.${f.name}: ${t.javaClass.simpleName}")
            }
        }
    }

    private val SKIP = Any()

    /**
     * Decide replacement value. Empty out collections; blank feature-config
     * strings so `isEnableFeature()` returns false; flip `isPreloaded`/similar
     * guard booleans to true so preload work is skipped. Leave everything else
     * alone (don't touch `sInstance`, singleton refs, constants like TAG).
     */
    private fun replacementFor(name: String, type: Class<*>): Any? {
        // Boolean guards that mean "work already done, don't retry"
        if (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java) {
            return when (name) {
                "isPreloaded", "sPreloaded" -> true
                else -> SKIP
            }
        }
        // Feature-config strings — make isEnableFeature() return false
        if (type == String::class.java) {
            return when (name) {
                "sFeatureConfig" -> "0"
                else -> SKIP
            }
        }
        // Collections — empty out
        if (type.isArray) {
            return java.lang.reflect.Array.newInstance(type.componentType!!, 0)
        }
        if (java.util.Map::class.java.isAssignableFrom(type)) return emptyMapOf(type)
        if (java.util.List::class.java.isAssignableFrom(type)) return ArrayList<Any>()
        if (java.util.Set::class.java.isAssignableFrom(type)) return HashSet<Any>()
        if (java.util.Collection::class.java.isAssignableFrom(type)) return ArrayList<Any>()
        // Everything else (singletons, lock objects) — leave alone
        return SKIP
    }

    private fun emptyMapOf(type: Class<*>): Any {
        // ArrayMap lives in android.util — use it if the field expects it to
        // keep Miui's internal iterator expectations happy; else fall back.
        return try {
            if (type.name == "android.util.ArrayMap") {
                Class.forName("android.util.ArrayMap").getDeclaredConstructor().newInstance()
            } else {
                HashMap<Any, Any>()
            }
        } catch (_: Throwable) {
            HashMap<Any, Any>()
        }
    }

    private fun isCollectionType(type: Class<*>): Boolean {
        return type.isArray ||
            java.util.Map::class.java.isAssignableFrom(type) ||
            java.util.Collection::class.java.isAssignableFrom(type)
    }

    private fun describe(v: Any?): String {
        if (v == null) return "null"
        val s = v.toString()
        return if (s.length > 60) s.substring(0, 60) + "…" else s
    }
}
