package app.pwhs.dunebox.sdk.rule

import com.google.gson.annotations.SerializedName

/**
 * Defines rules for a specific package running inside the DuneBox sandbox.
 * Rules control component access, process management, IO operations, device spoofing, and locale.
 *
 * Create via Kotlin DSL:
 * ```
 * val rule = packageRule("com.example.app") {
 *     components {
 *         blacklistActivity("com.example.SplashActivity")
 *     }
 *     io {
 *         deny("/proc/self/maps")
 *         redirect("/data/local/tmp", "/data/data/app.pwhs.dunebox/virtual/tmp")
 *     }
 *     device {
 *         hideRoot = true
 *     }
 * }
 * ```
 *
 * Or via Java Builder:
 * ```java
 * PackageRule rule = new PackageRule.Builder("com.example.app")
 *     .addBlacklistActivity("com.example.SplashActivity")
 *     .hideRoot(true)
 *     .build();
 * ```
 */
data class PackageRule(
    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("scoped_processes")
    val scopedProcesses: List<String> = emptyList(),

    @SerializedName("components")
    val componentRules: ComponentRules = ComponentRules(),

    @SerializedName("process")
    val processRules: ProcessRules = ProcessRules(),

    @SerializedName("io")
    val ioRules: IORules = IORules(),

    @SerializedName("device")
    val deviceRules: DeviceRules = DeviceRules(),

    @SerializedName("locale")
    val localeRules: LocaleRules = LocaleRules(),
) {

    /**
     * Java-friendly builder for PackageRule.
     */
    class Builder(private val packageName: String) {
        private val processes = mutableListOf<String>()
        private val blackActivities = mutableListOf<String>()
        private val blackServices = mutableListOf<String>()
        private val blackBroadcasts = mutableListOf<String>()
        private val blackProviders = mutableListOf<String>()
        private val preloadProcesses = mutableListOf<String>()
        private val blackProcesses = mutableListOf<String>()
        private val denyPaths = mutableListOf<String>()
        private val redirectPaths = mutableListOf<IORedirectEntry>()
        private var hideRoot = false
        private var hideSim = false
        private var hideVpn = false
        private var hideXposed = false
        private var fakeBrand: String? = null
        private var fakeModel: String? = null
        private var fakeFingerprint: String? = null
        private var language: String? = null
        private var region: String? = null
        private var timeZone: String? = null

        fun addProcess(process: String) = apply { processes.add(process) }
        fun addBlacklistActivity(name: String) = apply { blackActivities.add(name) }
        fun addBlacklistService(name: String) = apply { blackServices.add(name) }
        fun addBlacklistBroadcast(name: String) = apply { blackBroadcasts.add(name) }
        fun addBlacklistProvider(name: String) = apply { blackProviders.add(name) }
        fun addPreloadProcess(name: String) = apply { preloadProcesses.add(name) }
        fun addBlacklistProcess(name: String) = apply { blackProcesses.add(name) }
        fun addDenyPath(path: String) = apply { denyPaths.add(path) }
        fun addRedirectPath(from: String, to: String) = apply { redirectPaths.add(IORedirectEntry(from, to)) }
        fun hideRoot(hide: Boolean) = apply { hideRoot = hide }
        fun hideSim(hide: Boolean) = apply { hideSim = hide }
        fun hideVpn(hide: Boolean) = apply { hideVpn = hide }
        fun hideXposed(hide: Boolean) = apply { hideXposed = hide }
        fun setFakeBrand(brand: String?) = apply { fakeBrand = brand }
        fun setFakeModel(model: String?) = apply { fakeModel = model }
        fun setFakeFingerprint(fp: String?) = apply { fakeFingerprint = fp }
        fun setLanguage(lang: String?) = apply { language = lang }
        fun setRegion(region: String?) = apply { this.region = region }
        fun setTimeZone(tz: String?) = apply { timeZone = tz }

        fun build(): PackageRule = PackageRule(
            packageName = packageName,
            scopedProcesses = processes.toList(),
            componentRules = ComponentRules(
                blacklistActivities = blackActivities.toList(),
                blacklistServices = blackServices.toList(),
                blacklistBroadcasts = blackBroadcasts.toList(),
                blacklistProviders = blackProviders.toList(),
            ),
            processRules = ProcessRules(
                preloadProcesses = preloadProcesses.toList(),
                blacklistProcesses = blackProcesses.toList(),
            ),
            ioRules = IORules(
                denyPaths = denyPaths.toList(),
                redirectPaths = redirectPaths.toList(),
            ),
            deviceRules = DeviceRules(
                hideRoot = hideRoot,
                hideSim = hideSim,
                hideVpn = hideVpn,
                hideXposed = hideXposed,
                fakeBrand = fakeBrand,
                fakeModel = fakeModel,
                fakeFingerprint = fakeFingerprint,
            ),
            localeRules = LocaleRules(
                language = language,
                region = region,
                timeZone = timeZone,
            ),
        )
    }
}

// ==========================================
// Sub-rule data classes
// ==========================================

data class ComponentRules(
    @SerializedName("blacklist_activities")
    val blacklistActivities: List<String> = emptyList(),

    @SerializedName("blacklist_services")
    val blacklistServices: List<String> = emptyList(),

    @SerializedName("blacklist_broadcasts")
    val blacklistBroadcasts: List<String> = emptyList(),

    @SerializedName("blacklist_providers")
    val blacklistProviders: List<String> = emptyList(),
)

data class ProcessRules(
    @SerializedName("preload_processes")
    val preloadProcesses: List<String> = emptyList(),

    @SerializedName("blacklist_processes")
    val blacklistProcesses: List<String> = emptyList(),
)

data class IORules(
    @SerializedName("deny_paths")
    val denyPaths: List<String> = emptyList(),

    @SerializedName("redirect_paths")
    val redirectPaths: List<IORedirectEntry> = emptyList(),
)

data class IORedirectEntry(
    @SerializedName("from")
    val from: String,

    @SerializedName("to")
    val to: String,
)

data class DeviceRules(
    @SerializedName("hide_root")
    val hideRoot: Boolean = false,

    @SerializedName("hide_sim")
    val hideSim: Boolean = false,

    @SerializedName("hide_vpn")
    val hideVpn: Boolean = false,

    @SerializedName("hide_xposed")
    val hideXposed: Boolean = false,

    @SerializedName("fake_brand")
    val fakeBrand: String? = null,

    @SerializedName("fake_model")
    val fakeModel: String? = null,

    @SerializedName("fake_fingerprint")
    val fakeFingerprint: String? = null,
)

data class LocaleRules(
    @SerializedName("language")
    val language: String? = null,

    @SerializedName("region")
    val region: String? = null,

    @SerializedName("time_zone")
    val timeZone: String? = null,
)
