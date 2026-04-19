package app.pwhs.dunebox.sdk.internal.engine

import android.content.ComponentName
import android.content.Intent
import timber.log.Timber

/**
 * Manages the allocation of Stub components to virtual app components.
 *
 * When a virtual app wants to start an Activity, we need to:
 * 1. Pick an available StubActivity from the pool
 * 2. Create an Intent targeting the StubActivity
 * 3. Save the original Intent data for later extraction
 *
 * Key constants for Intent extras used to smuggle real component info
 * through the Android framework's manifest check.
 */
internal object StubActivityManager {

    /** Intent extra key: the real target Activity class name */
    const val EXTRA_REAL_TARGET = "_dunebox_real_target"
    /** Intent extra key: the virtual app's package name */
    const val EXTRA_VIRTUAL_PKG = "_dunebox_virtual_pkg"
    /** Intent extra key: the original Intent (as Parcelable) */
    const val EXTRA_ORIGINAL_INTENT = "_dunebox_original_intent"

    /** Host app package (DuneBox itself) */
    private var hostPackage: String = ""

    // Pool of stub class names per process
    private val stubPool = listOf(
        "app.pwhs.dunebox.stub.StubActivity\$P0\$S0",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$S1",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$S2",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$S3",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$S4",
    )

    private val singleTaskStubs = listOf(
        "app.pwhs.dunebox.stub.StubActivity\$P0\$T0",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$T1",
        "app.pwhs.dunebox.stub.StubActivity\$P0\$T2",
    )

    // Track which stubs are currently in use
    // StubClassName -> RealClassName
    private val activeStubs = mutableMapOf<String, String>()

    fun init(hostPkg: String) {
        hostPackage = hostPkg
        Timber.d("StubActivityManager initialized for host: $hostPkg")
    }

    /**
     * Wrap an intent targeting a virtual Activity into a StubActivity intent.
     * The original intent is saved inside extras.
     *
     * @param originalIntent The intent the virtual app wants to start
     * @param virtualPackage The virtual app's package name
     * @return New intent targeting a StubActivity, or null if no stubs available
     */
    fun wrapIntent(originalIntent: Intent, virtualPackage: String): Intent? {
        val realTarget = originalIntent.component?.className
            ?: return null

        // Find an available stub
        val stubClass = allocateStub(realTarget) ?: run {
            Timber.e("No available stub activities!")
            return null
        }

        // Create stub intent
        val stubIntent = Intent(originalIntent)
        stubIntent.component = ComponentName(hostPackage, stubClass)

        // Smuggle the real target info
        stubIntent.putExtra(EXTRA_REAL_TARGET, realTarget)
        stubIntent.putExtra(EXTRA_VIRTUAL_PKG, virtualPackage)
        stubIntent.putExtra(EXTRA_ORIGINAL_INTENT, originalIntent)

        Timber.d("Wrapped intent: $realTarget -> $stubClass")
        return stubIntent
    }

    /**
     * Create an intent to launch the main Activity of a virtual app.
     *
     * @param packageName Virtual app's package name
     * @param mainActivity The launcher activity class name
     * @return Intent targeting a StubActivity with real target info embedded
     */
    fun createLaunchIntent(packageName: String, mainActivity: String): Intent {
        val stubClass = allocateStub(mainActivity)
            ?: stubPool.first() // fallback to first stub

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(hostPackage, stubClass)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_REAL_TARGET, mainActivity)
            putExtra(EXTRA_VIRTUAL_PKG, packageName)
        }

        Timber.d("Created launch intent: $mainActivity via $stubClass")
        return intent
    }

    /**
     * Extract the real target class name from a stub intent.
     */
    fun extractRealTarget(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_REAL_TARGET)
    }

    /**
     * Extract the virtual package name from a stub intent.
     */
    fun extractVirtualPackage(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_VIRTUAL_PKG)
    }

    /**
     * Check if a class name is one of our stubs.
     */
    fun isStubActivity(className: String?): Boolean {
        return className != null && (
                stubPool.contains(className) ||
                        singleTaskStubs.contains(className)
                )
    }

    /**
     * Release a stub when its Activity is destroyed.
     */
    fun releaseStub(stubClass: String) {
        activeStubs.remove(stubClass)
        Timber.d("Released stub: $stubClass")
    }

    /**
     * Allocate a stub for a real Activity class.
     * Re-uses existing allocation if the same real class is already mapped.
     */
    private fun allocateStub(realClass: String): String? {
        // Check if already allocated
        activeStubs.entries.find { it.value == realClass }?.let {
            return it.key
        }

        // Find an unused stub
        val available = stubPool.find { it !in activeStubs }
        if (available != null) {
            activeStubs[available] = realClass
            Timber.d("Allocated stub $available for $realClass")
        }
        return available
    }
}
