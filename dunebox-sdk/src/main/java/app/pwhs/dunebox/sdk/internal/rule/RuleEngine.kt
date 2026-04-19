package app.pwhs.dunebox.sdk.internal.rule

import app.pwhs.dunebox.sdk.rule.PackageRule
import app.pwhs.dunebox.sdk.rule.RuleSet
import timber.log.Timber

/**
 * Internal rule engine that manages PackageRule lookup and application.
 * Rules are indexed by packageName for O(1) lookup.
 */
internal class RuleEngine {

    private val rules = mutableMapOf<String, PackageRule>()

    /**
     * Add a single rule. If a rule for the same package already exists, it will be replaced.
     */
    fun addRule(rule: PackageRule) {
        rules[rule.packageName] = rule
        Timber.d("Rule added/updated for package: ${rule.packageName}")
    }

    /**
     * Add multiple rules from a RuleSet.
     */
    fun addRuleSet(ruleSet: RuleSet) {
        ruleSet.rules.forEach { addRule(it) }
        Timber.d("RuleSet added: ${ruleSet.rules.size} rules")
    }

    /**
     * Remove the rule for a specific package.
     */
    fun removeRule(packageName: String) {
        rules.remove(packageName)
        Timber.d("Rule removed for package: $packageName")
    }

    /**
     * Get the rule for a specific package, or null if none exists.
     */
    fun getRule(packageName: String): PackageRule? = rules[packageName]

    /**
     * Get all registered rules.
     */
    fun getAllRules(): List<PackageRule> = rules.values.toList()

    /**
     * Check if an Activity is blacklisted for a given package.
     */
    fun isActivityBlacklisted(packageName: String, activityName: String): Boolean {
        return getRule(packageName)?.componentRules?.blacklistActivities?.contains(activityName) == true
    }

    /**
     * Check if a Service is blacklisted for a given package.
     */
    fun isServiceBlacklisted(packageName: String, serviceName: String): Boolean {
        return getRule(packageName)?.componentRules?.blacklistServices?.contains(serviceName) == true
    }

    /**
     * Check if a BroadcastReceiver is blacklisted for a given package.
     */
    fun isBroadcastBlacklisted(packageName: String, broadcastName: String): Boolean {
        return getRule(packageName)?.componentRules?.blacklistBroadcasts?.contains(broadcastName) == true
    }

    /**
     * Check if a ContentProvider is blacklisted for a given package.
     */
    fun isProviderBlacklisted(packageName: String, providerName: String): Boolean {
        return getRule(packageName)?.componentRules?.blacklistProviders?.contains(providerName) == true
    }

    /**
     * Check if a process is blacklisted for a given package.
     */
    fun isProcessBlacklisted(packageName: String, processName: String): Boolean {
        return getRule(packageName)?.processRules?.blacklistProcesses?.contains(processName) == true
    }

    /**
     * Get preload process names for a package.
     */
    fun getPreloadProcesses(packageName: String): List<String> {
        return getRule(packageName)?.processRules?.preloadProcesses ?: emptyList()
    }

    /**
     * Clear all rules.
     */
    fun clearAll() {
        rules.clear()
        Timber.d("All rules cleared")
    }
}
