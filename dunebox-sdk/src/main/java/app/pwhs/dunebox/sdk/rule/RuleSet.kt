package app.pwhs.dunebox.sdk.rule

import app.pwhs.dunebox.sdk.DuneBoxDsl
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * A collection of [PackageRule]s that can be serialized to/from JSON
 * for local persistence or remote cloud configuration.
 *
 * Usage:
 * ```
 * val rules = ruleSet {
 *     rule(wechatRule)
 *     rule(telegramRule)
 * }
 *
 * // Serialize to JSON for cloud config
 * val json = rules.toJson()
 *
 * // Deserialize from JSON
 * val fromRemote = RuleSet.fromJson(jsonString)
 * ```
 */
data class RuleSet(
    val rules: List<PackageRule>
) {

    /**
     * Serialize this RuleSet to a JSON string.
     */
    fun toJson(): String = gson.toJson(this)

    /**
     * Find a rule by package name.
     */
    fun findRule(packageName: String): PackageRule? {
        return rules.find { it.packageName == packageName }
    }

    /**
     * Merge with another RuleSet. Rules for the same package will be replaced.
     */
    fun merge(other: RuleSet): RuleSet {
        val merged = rules.associateBy { it.packageName }.toMutableMap()
        other.rules.forEach { merged[it.packageName] = it }
        return RuleSet(merged.values.toList())
    }

    companion object {
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        /**
         * Deserialize a RuleSet from a JSON string.
         */
        fun fromJson(json: String): RuleSet {
            return gson.fromJson(json, RuleSet::class.java)
        }

        /**
         * Create an empty RuleSet.
         */
        fun empty(): RuleSet = RuleSet(emptyList())
    }
}

/**
 * DSL function for creating a [RuleSet].
 */
fun ruleSet(block: RuleSetScope.() -> Unit): RuleSet {
    return RuleSetScope().apply(block).build()
}

@DuneBoxDsl
class RuleSetScope {
    private val rules = mutableListOf<PackageRule>()

    /** Add an existing rule to this set. */
    fun rule(rule: PackageRule) {
        rules.add(rule)
    }

    /** Create and add a rule inline. */
    fun rule(packageName: String, block: PackageRuleScope.() -> Unit) {
        rules.add(packageRule(packageName, block))
    }

    internal fun build(): RuleSet = RuleSet(rules.toList())
}
