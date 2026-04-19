package app.pwhs.dunebox.sdk

import android.content.Context
import android.content.Intent
import app.pwhs.dunebox.sdk.callback.DuneBoxCallback
import app.pwhs.dunebox.sdk.config.DuneBoxConfig
import app.pwhs.dunebox.sdk.config.LogLevel
import app.pwhs.dunebox.sdk.event.DuneBoxEvent
import app.pwhs.dunebox.sdk.internal.engine.StubActivityManager
import app.pwhs.dunebox.sdk.internal.engine.VirtualAppRegistry
import app.pwhs.dunebox.sdk.internal.hook.BinderHookManager
import app.pwhs.dunebox.sdk.internal.loader.ApkParser
import app.pwhs.dunebox.sdk.internal.loader.DexLoader
import app.pwhs.dunebox.sdk.internal.rule.RuleEngine
import app.pwhs.dunebox.sdk.internal.vfs.VirtualFileSystem
import app.pwhs.dunebox.sdk.model.VirtualAppInfo
import app.pwhs.dunebox.sdk.rule.PackageRule
import app.pwhs.dunebox.sdk.rule.RuleSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File

/**
 * DuneBox SDK — Android Virtual Engine.
 *
 * Main entry point for all SDK operations. Initialize in Application.attachBaseContext():
 *
 * ```kotlin
 * override fun attachBaseContext(base: Context) {
 *     super.attachBaseContext(base)
 *     DuneBox.init(this) {
 *         logLevel = LogLevel.DEBUG
 *         maxVirtualUsers = 3
 *     }
 *     if (DuneBox.isClientProcess()) return
 * }
 * ```
 */
object DuneBox {

    private var isInitialized = false
    private var config = DuneBoxConfig()

    private lateinit var appContext: Context
    private lateinit var vfs: VirtualFileSystem
    private lateinit var apkParser: ApkParser
    private lateinit var dexLoader: DexLoader

    private val ruleEngine = RuleEngine()
    private val callbacks = mutableListOf<DuneBoxCallback>()

    private val _events = MutableSharedFlow<DuneBoxEvent>(extraBufferCapacity = 64)

    /** SharedFlow of SDK events. Collect in a coroutine to receive updates. */
    val events: SharedFlow<DuneBoxEvent> = _events.asSharedFlow()

    // =================================================================
    // Initialization
    // =================================================================

    /**
     * Initialize the DuneBox SDK. Must be called from Application.attachBaseContext().
     *
     * @param context Application context
     * @param configBlock Optional DSL configuration block
     */
    fun init(context: Context, configBlock: DuneBoxConfig.() -> Unit = {}) {
        if (isInitialized) {
            Timber.w("DuneBox already initialized")
            return
        }

        // getApplicationContext() can be null during attachBaseContext
        appContext = context.applicationContext ?: context

        // Apply config
        config = DuneBoxConfig().apply(configBlock)
        config.validate()

        // Setup logging
        setupLogging(config.logLevel)

        Timber.i("DuneBox SDK initializing...")

        // Initialize subsystems
        try {
            // 1. Virtual File System
            val virtualBase = File(appContext.filesDir, "virtual")
            vfs = VirtualFileSystem(virtualBase)
            vfs.init()
            Timber.d("VFS initialized")

            // 2. APK Parser
            apkParser = ApkParser(appContext)
            Timber.d("ApkParser initialized")

            // 3. DexLoader
            dexLoader = DexLoader(appContext)
            Timber.d("DexLoader initialized")

            // 4. Binder Hooks (includes Instrumentation hook)
            BinderHookManager.init(appContext, config.enableBinderHook)
            Timber.d("BinderHookManager initialized")

            // 4. IO Redirect
            if (config.enableIORedirect) {
                // IO redirect is lazy — hooks are installed but rules are applied per-app
                Timber.d("IO redirect enabled (lazy init)")
            }

            isInitialized = true
            Timber.i("DuneBox SDK initialized successfully")
            _events.tryEmit(DuneBoxEvent.Initialized(success = true))
        } catch (e: Exception) {
            Timber.e(e, "DuneBox SDK initialization failed")
            _events.tryEmit(DuneBoxEvent.Initialized(success = false, message = e.message ?: "Unknown error"))
        }
    }

    /**
     * Check if the current process is a virtual client process.
     * If true, the host app should skip its normal initialization.
     */
    fun isClientProcess(): Boolean {
        // TODO: Check process name to determine if this is a virtual process (:p0, :p1, etc.)
        return false
    }

    // =================================================================
    // App Management
    // =================================================================

    /**
     * Clone an installed app into the sandbox by package name.
     *
     * @param packageName Package name of the app to clone
     * @param userId Virtual user ID (default: 0)
     * @return Result containing VirtualAppInfo on success
     */
    fun installPackage(packageName: String, userId: Int = 0): Result<VirtualAppInfo> {
        ensureInitialized()

        Timber.i("Installing package: $packageName (userId=$userId)")

        return try {
            // 1. Parse the installed app's APK
            val parsedInfo = apkParser.parseInstalled(packageName)
                ?: return Result.failure(IllegalArgumentException("Package not found: $packageName"))

            // 2. Copy APK to DuneBox storage
            val sourceApk = apkParser.getApkPath(packageName)
                ?: return Result.failure(IllegalStateException("Cannot get APK path for: $packageName"))

            val destApk = File(vfs.getApkDir(), "$packageName.apk")
            File(sourceApk).copyTo(destApk, overwrite = true)
            Timber.d("APK copied: $sourceApk -> ${destApk.absolutePath}")

            // 3. Create virtual directories
            vfs.createVirtualDirs(packageName, userId)

            // 4. Create VirtualAppInfo
            val appInfo = VirtualAppInfo(
                packageName = parsedInfo.packageName,
                appName = parsedInfo.appName,
                versionName = parsedInfo.versionName,
                versionCode = parsedInfo.versionCode,
                userId = userId,
                apkPath = destApk.absolutePath,
                dataDir = vfs.getDataDir(packageName, userId).absolutePath,
                icon = parsedInfo.icon,
            )

            // 5. Register in VirtualAppRegistry
            VirtualAppRegistry.register(
                VirtualAppRegistry.AppEntry(
                    packageName = parsedInfo.packageName,
                    userId = userId,
                    apkPath = destApk.absolutePath,
                    dataDir = vfs.getDataDir(packageName, userId).absolutePath,
                    parsedInfo = parsedInfo,
                )
            )

            // 6. Emit event
            _events.tryEmit(DuneBoxEvent.AppInstalled(packageName, userId))
            callbacks.forEach { it.onAppInstalled(packageName, userId) }

            Timber.i("Package installed successfully: $packageName")
            Result.success(appInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install package: $packageName")
            Result.failure(e)
        }
    }

    /**
     * Clone an app from an APK file.
     */
    fun installPackage(apkFile: File, userId: Int = 0): Result<VirtualAppInfo> {
        ensureInitialized()

        val parsedInfo = apkParser.parse(apkFile)
            ?: return Result.failure(IllegalArgumentException("Cannot parse APK: ${apkFile.absolutePath}"))

        // Copy APK to internal storage
        val destApk = File(vfs.getApkDir(), "${parsedInfo.packageName}.apk")
        apkFile.copyTo(destApk, overwrite = true)

        // Create virtual dirs
        vfs.createVirtualDirs(parsedInfo.packageName, userId)

        val appInfo = VirtualAppInfo(
            packageName = parsedInfo.packageName,
            appName = parsedInfo.appName,
            versionName = parsedInfo.versionName,
            versionCode = parsedInfo.versionCode,
            userId = userId,
            apkPath = destApk.absolutePath,
            dataDir = vfs.getDataDir(parsedInfo.packageName, userId).absolutePath,
            icon = parsedInfo.icon,
        )

        _events.tryEmit(DuneBoxEvent.AppInstalled(parsedInfo.packageName, userId))
        callbacks.forEach { it.onAppInstalled(parsedInfo.packageName, userId) }

        return Result.success(appInfo)
    }

    /**
     * Remove a cloned app from the sandbox.
     */
    fun uninstallPackage(packageName: String, userId: Int = 0): Result<Unit> {
        ensureInitialized()

        return try {
            // Delete virtual data
            vfs.deleteVirtualData(packageName, userId)

            // Delete copied APK
            val apkFile = File(vfs.getApkDir(), "$packageName.apk")
            if (apkFile.exists()) apkFile.delete()

            // Remove rules
            ruleEngine.removeRule(packageName)

            _events.tryEmit(DuneBoxEvent.AppUninstalled(packageName, userId))
            callbacks.forEach { it.onAppUninstalled(packageName, userId) }

            Timber.i("Package uninstalled: $packageName")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to uninstall: $packageName")
            Result.failure(e)
        }
    }

    /**
     * Check if a package is installed in the sandbox.
     */
    fun isInstalled(packageName: String, userId: Int = 0): Boolean {
        ensureInitialized()
        val apkFile = File(vfs.getApkDir(), "$packageName.apk")
        return apkFile.exists()
    }

    /**
     * Get all installed virtual apps.
     */
    fun getInstalledApps(userId: Int = 0): List<VirtualAppInfo> {
        ensureInitialized()
        // List APKs in the storage directory and parse each
        val apkDir = vfs.getApkDir()
        return apkDir.listFiles { file -> file.extension == "apk" }?.mapNotNull { apkFile ->
            val parsed = apkParser.parse(apkFile) ?: return@mapNotNull null
            VirtualAppInfo(
                packageName = parsed.packageName,
                appName = parsed.appName,
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                userId = userId,
                apkPath = apkFile.absolutePath,
                dataDir = vfs.getDataDir(parsed.packageName, userId).absolutePath,
                icon = parsed.icon,
            )
        } ?: emptyList()
    }

    /**
     * Get info for a specific virtual app.
     */
    fun getAppInfo(packageName: String, userId: Int = 0): VirtualAppInfo? {
        return getInstalledApps(userId).find { it.packageName == packageName }
    }

    /**
     * Launch a virtual app.
     */
    fun launchApp(packageName: String, userId: Int = 0) {
        ensureInitialized()

        Timber.i("Launching virtual app: $packageName (userId=$userId)")

        // 1. Setup IO redirect for this app
        vfs.setupRedirect(packageName, userId)

        // 2. Apply package-specific IO rules
        ruleEngine.getRule(packageName)?.let { rule ->
            vfs.applyIORules(rule.ioRules)
        }

        // 3. Start IO redirect if not already active
        if (config.enableIORedirect) {
            vfs.startRedirect()
        }

        // 4. Get the app entry from registry
        val appEntry = VirtualAppRegistry.getApp(packageName)
        if (appEntry == null) {
            Timber.e("App not registered: $packageName. Install it first.")
            return
        }

        // 5. Create DexClassLoader if not yet loaded
        if (appEntry.classLoader == null) {
            val loadResult = dexLoader.loadApk(
                appEntry.apkPath,
                appEntry.dataDir,
            )
            if (loadResult == null) {
                Timber.e("Failed to load APK for: $packageName")
                return
            }
            appEntry.classLoader = loadResult.classLoader
            Timber.i("APK loaded into memory: $packageName")
        }

        // 6. Set as active package
        VirtualAppRegistry.setActivePackage(packageName)

        // 7. Find the main/launcher Activity
        val mainActivity = appEntry.parsedInfo.mainActivity
        if (mainActivity == null) {
            Timber.e("No main activity found for: $packageName")
            return
        }

        // 8. Create launch intent via StubActivityManager
        val launchIntent = StubActivityManager.createLaunchIntent(packageName, mainActivity)

        // 9. Start the StubActivity (framework sees a valid manifest component)
        //    HookInstrumentation will intercept and swap with real Activity
        try {
            appContext.startActivity(launchIntent)
            Timber.i("Started StubActivity for: $mainActivity")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start activity for: $packageName")
            return
        }

        _events.tryEmit(DuneBoxEvent.AppLaunched(packageName, userId, android.os.Process.myPid()))
        callbacks.forEach { it.onAppStarted(packageName, userId, android.os.Process.myPid()) }

        Timber.i("Virtual app launched: $packageName")
    }

    /**
     * Stop a virtual app.
     */
    fun stopApp(packageName: String, userId: Int = 0) {
        ensureInitialized()
        vfs.stopRedirect()
        _events.tryEmit(DuneBoxEvent.AppStopped(packageName, userId))
        callbacks.forEach { it.onAppStopped(packageName, userId) }
        Timber.i("Virtual app stopped: $packageName")
    }

    // =================================================================
    // Rule System
    // =================================================================

    /** Add a rule for a specific package. */
    fun addRule(rule: PackageRule) {
        ensureInitialized()
        ruleEngine.addRule(rule)
    }

    /** Add a set of rules. */
    fun addRuleSet(ruleSet: RuleSet) {
        ensureInitialized()
        ruleEngine.addRuleSet(ruleSet)
    }

    /** Add rules from a JSON string (for remote config). */
    fun addRuleSetFromJson(json: String) {
        val ruleSet = RuleSet.fromJson(json)
        addRuleSet(ruleSet)
    }

    /** Remove the rule for a specific package. */
    fun removeRule(packageName: String) {
        ruleEngine.removeRule(packageName)
    }

    /** Get all registered rules. */
    fun getRules(): List<PackageRule> = ruleEngine.getAllRules()

    // =================================================================
    // Callbacks
    // =================================================================

    /** Register a callback for lifecycle events. */
    fun registerCallback(callback: DuneBoxCallback) {
        callbacks.add(callback)
    }

    /** Unregister a previously registered callback. */
    fun unregisterCallback(callback: DuneBoxCallback) {
        callbacks.remove(callback)
    }

    // =================================================================
    // Internals
    // =================================================================

    private fun ensureInitialized() {
        check(isInitialized) { "DuneBox not initialized. Call DuneBox.init(context) in Application.attachBaseContext()" }
    }

    private fun setupLogging(level: LogLevel) {
        if (Timber.treeCount == 0 && level != LogLevel.NONE) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
