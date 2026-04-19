package app.pwhs.dunebox.sdk.internal.hook

import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Utility class for reflection operations used in Binder hooking.
 * Caches Field/Method objects to minimize reflection overhead.
 */
internal object ReflectUtils {

    private val fieldCache = mutableMapOf<String, Field?>()
    private val methodCache = mutableMapOf<String, Method?>()

    /**
     * Get a field from a class, including private/hidden fields.
     * Results are cached for performance.
     */
    fun getField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}#$fieldName"
        return fieldCache.getOrPut(key) {
            try {
                var cls: Class<*>? = clazz
                while (cls != null) {
                    try {
                        val field = cls.getDeclaredField(fieldName)
                        field.isAccessible = true
                        return@getOrPut field
                    } catch (_: NoSuchFieldException) {
                        cls = cls.superclass
                    }
                }
                Timber.w("Field not found: $key")
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to get field: $key")
                null
            }
        }
    }

    /**
     * Get a method from a class, including private/hidden methods.
     * Results are cached for performance.
     */
    fun getMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        val key = "${clazz.name}#$methodName(${paramTypes.joinToString { it.name }})"
        return methodCache.getOrPut(key) {
            try {
                var cls: Class<*>? = clazz
                while (cls != null) {
                    try {
                        val method = cls.getDeclaredMethod(methodName, *paramTypes)
                        method.isAccessible = true
                        return@getOrPut method
                    } catch (_: NoSuchMethodException) {
                        cls = cls.superclass
                    }
                }
                Timber.w("Method not found: $key")
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to get method: $key")
                null
            }
        }
    }

    /**
     * Get a field value from an object.
     */
    fun getFieldValue(obj: Any, fieldName: String): Any? {
        val field = getField(obj.javaClass, fieldName) ?: return null
        return try {
            field.get(obj)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get field value: $fieldName")
            null
        }
    }

    /**
     * Set a field value on an object.
     */
    fun setFieldValue(obj: Any, fieldName: String, value: Any?): Boolean {
        val field = getField(obj.javaClass, fieldName) ?: return false
        return try {
            field.set(obj, value)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set field value: $fieldName")
            false
        }
    }

    /**
     * Get a static field value from a class.
     */
    fun getStaticFieldValue(clazz: Class<*>, fieldName: String): Any? {
        val field = getField(clazz, fieldName) ?: return null
        return try {
            field.get(null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get static field value: ${clazz.name}#$fieldName")
            null
        }
    }

    /**
     * Set a static field value on a class.
     */
    fun setStaticFieldValue(clazz: Class<*>, fieldName: String, value: Any?): Boolean {
        val field = getField(clazz, fieldName) ?: return false
        return try {
            field.set(null, value)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set static field value: ${clazz.name}#$fieldName")
            false
        }
    }

    /**
     * Clear all cached reflection data.
     */
    fun clearCache() {
        fieldCache.clear()
        methodCache.clear()
    }
}
