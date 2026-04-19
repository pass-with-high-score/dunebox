package app.pwhs.dunebox.sample

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Simple test Activity for validating DuneBox clone functionality.
 *
 * Features:
 * - Displays a counter that persists via SharedPreferences
 * - Each launch increments the counter
 * - Button to manually increment
 * - Shows package name and data directory for debugging
 *
 * Success criteria for DuneBox:
 * 1. This Activity renders correctly inside DuneBox
 * 2. Counter increments and persists across launches
 * 3. Data is isolated from the original app installation
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var counterText: TextView
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("dunebox_test", MODE_PRIVATE)

        // Increment launch counter
        val launchCount = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launchCount).apply()

        // Build UI programmatically (no XML dependency)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleText = TextView(this).apply {
            text = "🏜️ Hello DuneBox!"
            textSize = 28f
            setPadding(0, 0, 0, 24)
        }

        counterText = TextView(this).apply {
            text = "Launch count: $launchCount"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }

        val tapCount = prefs.getInt("tap_count", 0)
        val tapText = TextView(this).apply {
            text = "Tap count: $tapCount"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }

        val button = Button(this).apply {
            text = "Tap me! (+1)"
            setOnClickListener {
                val newCount = prefs.getInt("tap_count", 0) + 1
                prefs.edit().putInt("tap_count", newCount).apply()
                tapText.text = "Tap count: $newCount"
            }
        }

        infoText = TextView(this).apply {
            text = buildString {
                appendLine("--- Debug Info ---")
                appendLine("Package: ${packageName}")
                appendLine("Data dir: ${filesDir.absolutePath}")
                appendLine("Cache dir: ${cacheDir.absolutePath}")
                appendLine("PID: ${android.os.Process.myPid()}")
            }
            textSize = 12f
            setPadding(0, 32, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(counterText)
        layout.addView(tapText)
        layout.addView(button)
        layout.addView(infoText)

        setContentView(layout)
    }
}
