package app.pwhs.dunebox.sdk.rule

import app.pwhs.dunebox.sdk.DuneBoxDsl

/**
 * Kotlin DSL entry point for creating a [PackageRule].
 *
 * Usage:
 * ```
 * val rule = packageRule("com.tencent.mm") {
 *     processes("com.tencent.mm", "com.tencent.mm:tools")
 *
 *     components {
 *         blacklistActivity("com.tencent.mm.plugin.base.stub.WXEntryActivity")
 *         blacklistService("com.tencent.mm.plugin.backup.BackupPcService")
 *     }
 *
 *     io {
 *         deny("/proc/self/maps")
 *         redirect("/proc/self/cmdline", "/proc/self/fake-cmdline")
 *     }
 *
 *     device {
 *         hideRoot = true
 *         hideSim = true
 *     }
 *
 *     locale {
 *         language = "zh"
 *         region = "CN"
 *         timeZone = "Asia/Shanghai"
 *     }
 * }
 * ```
 */
fun packageRule(packageName: String, block: PackageRuleScope.() -> Unit): PackageRule {
    return PackageRuleScope(packageName).apply(block).build()
}

@DuneBoxDsl
class PackageRuleScope(private val packageName: String) {
    private val processes = mutableListOf<String>()
    private var componentScope: ComponentScope? = null
    private var processControlScope: ProcessControlScope? = null
    private var ioScope: IOScope? = null
    private var deviceScope: DeviceScope? = null
    private var localeScope: LocaleScope? = null

    /** Define scoped processes for this rule. */
    fun processes(vararg names: String) {
        processes.addAll(names)
    }

    /** Configure component blacklists. */
    fun components(block: ComponentScope.() -> Unit) {
        componentScope = ComponentScope().apply(block)
    }

    /** Configure process control rules. */
    fun processControl(block: ProcessControlScope.() -> Unit) {
        processControlScope = ProcessControlScope().apply(block)
    }

    /** Configure IO redirect/deny rules. */
    fun io(block: IOScope.() -> Unit) {
        ioScope = IOScope().apply(block)
    }

    /** Configure device spoofing rules. */
    fun device(block: DeviceScope.() -> Unit) {
        deviceScope = DeviceScope().apply(block)
    }

    /** Configure locale override rules. */
    fun locale(block: LocaleScope.() -> Unit) {
        localeScope = LocaleScope().apply(block)
    }

    internal fun build(): PackageRule = PackageRule(
        packageName = packageName,
        scopedProcesses = processes.toList(),
        componentRules = componentScope?.build() ?: ComponentRules(),
        processRules = processControlScope?.build() ?: ProcessRules(),
        ioRules = ioScope?.build() ?: IORules(),
        deviceRules = deviceScope?.build() ?: DeviceRules(),
        localeRules = localeScope?.build() ?: LocaleRules(),
    )
}

@DuneBoxDsl
class ComponentScope {
    private val activities = mutableListOf<String>()
    private val services = mutableListOf<String>()
    private val broadcasts = mutableListOf<String>()
    private val providers = mutableListOf<String>()

    /** Blacklist an Activity by fully qualified class name. */
    fun blacklistActivity(className: String) { activities.add(className) }

    /** Blacklist a Service by fully qualified class name. */
    fun blacklistService(className: String) { services.add(className) }

    /** Blacklist a BroadcastReceiver by fully qualified class name. */
    fun blacklistBroadcast(className: String) { broadcasts.add(className) }

    /** Blacklist a ContentProvider by fully qualified class name. */
    fun blacklistProvider(className: String) { providers.add(className) }

    internal fun build() = ComponentRules(
        blacklistActivities = activities.toList(),
        blacklistServices = services.toList(),
        blacklistBroadcasts = broadcasts.toList(),
        blacklistProviders = providers.toList(),
    )
}

@DuneBoxDsl
class ProcessControlScope {
    private val preload = mutableListOf<String>()
    private val blacklist = mutableListOf<String>()

    /** Pre-start a process to speed up runtime. */
    fun preload(processName: String) { preload.add(processName) }

    /** Prevent a process from starting. */
    fun blacklist(processName: String) { blacklist.add(processName) }

    internal fun build() = ProcessRules(
        preloadProcesses = preload.toList(),
        blacklistProcesses = blacklist.toList(),
    )
}

@DuneBoxDsl
class IOScope {
    private val denyPaths = mutableListOf<String>()
    private val redirectPaths = mutableListOf<IORedirectEntry>()

    /** Deny access to a file or directory. */
    fun deny(path: String) { denyPaths.add(path) }

    /** Redirect file access from [from] to [to]. */
    fun redirect(from: String, to: String) { redirectPaths.add(IORedirectEntry(from, to)) }

    internal fun build() = IORules(
        denyPaths = denyPaths.toList(),
        redirectPaths = redirectPaths.toList(),
    )
}

@DuneBoxDsl
class DeviceScope {
    var hideRoot: Boolean = false
    var hideSim: Boolean = false
    var hideVpn: Boolean = false
    var hideXposed: Boolean = false
    var fakeBrand: String? = null
    var fakeModel: String? = null
    var fakeFingerprint: String? = null

    internal fun build() = DeviceRules(
        hideRoot = hideRoot,
        hideSim = hideSim,
        hideVpn = hideVpn,
        hideXposed = hideXposed,
        fakeBrand = fakeBrand,
        fakeModel = fakeModel,
        fakeFingerprint = fakeFingerprint,
    )
}

@DuneBoxDsl
class LocaleScope {
    var language: String? = null
    var region: String? = null
    var timeZone: String? = null

    internal fun build() = LocaleRules(
        language = language,
        region = region,
        timeZone = timeZone,
    )
}
