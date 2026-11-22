package dev.wizpor

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** Setup and settings screen. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var micStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var modelButton: Button
    private lateinit var a11yStatus: TextView
    private lateinit var batteryStatus: TextView
    @Volatile private var extracting = false
    @Volatile private var extractProgress = 0f
    private var extractError: String? = null

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, p, p, p * 3)
        }
        col.addView(text("wizpor", 28f, bold = true))
        col.addView(text("Version ${BuildConfig.VERSION_NAME} · on-device voice typing", 13f, muted = true))

        col.addView(header("1. Microphone"))
        micStatus = text("", 15f).also { col.addView(it) }
        col.addView(button("Allow microphone") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, "android.permission.POST_NOTIFICATIONS"), 1)
        })

        col.addView(header("2. Speech model"))
        modelStatus = text("", 15f).also { col.addView(it) }
        modelButton = button("Download (~${ModelManager.APPROX_MB} MB)") { startDownload() }.also { col.addView(it) }

        col.addView(header("3. Turn on the bubble"))
        a11yStatus = text("", 15f).also { col.addView(it) }
        col.addView(text(
            "Open Accessibility → Installed apps → wizpor → turn it on.\n\n" +
                "If wizpor is greyed out (\"Restricted setting\"): open App info below → ⋮ (top right) → " +
                "Allow restricted settings, then try again.", 14f, muted = true))
        col.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("Open App info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        col.addView(header("4. Keep it running"))
        batteryStatus = text("", 15f).also { col.addView(it) }
        col.addView(button("Allow background running") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })

        col.addView(header("Try it"))
        col.addView(text("Tap the box, then tap the floating bubble, speak, and tap it again. " +
            "Long-press the bubble while recording to cancel. Drag it to move it.", 14f, muted = true))
        col.addView(EditText(this).apply {
            hint = "Dictate here to test"
            minLines = 3
            gravity = Gravity.TOP
        })

        col.addView(header("Settings"))
        col.addView(CheckBox(this).apply {
            text = "Remove filler words (um, uh)"
            isChecked = prefs.removeFillers
            setOnCheckedChangeListener { _, v -> prefs.removeFillers = v }
        })
        col.addView(CheckBox(this).apply {
            text = "Only show the bubble when typing"
            isChecked = prefs.autoHide
            setOnCheckedChangeListener { _, v -> prefs.autoHide = v; BubbleService.instance?.updateVisibility() }
        })

        col.addView(header("Word list"))
        col.addView(text("Names and jargon you use, one per line, written the way you want them " +
            "(e.g. useEffect, Supabase).", 14f, muted = true))
        val words = EditText(this).apply {
            setText(prefs.words)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 4
            gravity = Gravity.TOP
            typeface = Typeface.MONOSPACE
        }
        col.addView(words)
        col.addView(button("Save word list") {
            prefs.words = words.text.toString()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })

        setContentView(ScrollView(this).apply { addView(col); isFillViewport = true })
    }

    override fun onResume() {
        super.onResume()
        // Starting the mic service while visible is what lets the bubble record over other apps.
        if (hasMic()) MicService.start(this)
        Engine.preload(this)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(poller)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasMic()) MicService.start(this)
        refresh()
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun a11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun refresh() {
        micStatus.text = if (hasMic()) "✓ Allowed" else "✗ Not allowed yet"
        a11yStatus.text = if (a11yEnabled()) "✓ Bubble is on" else "✗ Bubble is off"
        val pm = getSystemService(PowerManager::class.java)
        batteryStatus.text = if (pm.isIgnoringBatteryOptimizations(packageName)) "✓ Allowed"
        else "Recommended, so Samsung doesn't close the bubble"
        refreshModel()
    }

    private val poller = object : Runnable {
        override fun run() { refreshModel() }
    }

    private fun refreshModel() {
        main.removeCallbacks(poller)
        var poll = false
        when {
            ModelManager.isReady(this) -> {
                modelStatus.text = if (Engine.isLoaded) "✓ Ready (loaded)" else "✓ Downloaded"
                modelButton.visibility = View.GONE
            }
            extracting -> {
                modelStatus.text = "Unpacking… ${(extractProgress * 100).toInt()}% (keep this screen open)"
                modelButton.isEnabled = false
                poll = true
            }
            extractError != null -> {
                modelStatus.text = "✗ Unpack failed: $extractError"
                modelButton.isEnabled = true
            }
            else -> {
                val q = ModelManager.query(this)
                when (q?.status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                        val pct = if (q.total > 0) " ${q.done * 100 / q.total}%" else ""
                        val mb = q.done / 1_000_000
                        modelStatus.text = "Downloading…$pct ($mb MB)" +
                            if (q.status == DownloadManager.STATUS_PAUSED) " (paused, waiting for network)" else ""
                        modelButton.isEnabled = false
                        poll = true
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> { startExtract(); poll = true }
                    DownloadManager.STATUS_FAILED -> {
                        modelStatus.text = "✗ Download failed (code ${q.reason}). Try again."
                        modelButton.isEnabled = true
                    }
                    else -> {
                        modelStatus.text = "Not downloaded. Use Wi-Fi if you can."
                        modelButton.isEnabled = true
                    }
                }
            }
        }
        if (poll) main.postDelayed(poller, 700)
    }

    private fun startDownload() {
        extractError = null
        ModelManager.startDownload(this)
        refreshModel()
    }

    private fun startExtract() {
        if (extracting) return
        extracting = true
        extractError = null
        thread(name = "wizpor-extract") {
            try {
                ModelManager.extract(this) { extractProgress = it }
                Engine.preload(this)
            } catch (e: Exception) {
                extractError = e.message ?: e.javaClass.simpleName
            } finally {
                extracting = false
                main.post { refreshModel() }
            }
        }
    }

    // ---- tiny view helpers ----
    private fun text(s: String, size: Float, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (muted) setTextColor(Color.GRAY)
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun header(s: String) = text(s, 18f, bold = true).apply { setPadding(0, (24 * dp).toInt(), 0, (4 * dp).toInt()) }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
