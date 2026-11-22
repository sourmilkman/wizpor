package dev.wizpor

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Accessibility service that shows the floating bubble and types dictated text
 * into whichever text box is focused.
 */
class BubbleService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var bubble: BubbleView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: Prefs
    private var added = false
    private var shown = false
    private var target: AccessibilityNodeInfo? = null
    private var busy = false // recording or processing

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        wm = getSystemService(WindowManager::class.java)
        val size = (60 * resources.displayMetrics.density).toInt()
        bubble = BubbleView(this).apply { levelProvider = { Engine.level } }
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = if (prefs.bubbleX >= 0) prefs.bubbleX else dm.widthPixels - size
            y = if (prefs.bubbleY >= 0) prefs.bubbleY else (dm.heightPixels * 0.45f).toInt()
        }
        setupTouch()
        wm.addView(bubble, params)
        added = true
        shown = true
        updateVisibility()
        Engine.preload(this)
        instance = this
    }

    override fun onDestroy() {
        instance = null
        if (added) runCatching { wm.removeView(bubble) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        main.removeCallbacks(visibilityCheck)
        main.postDelayed(visibilityCheck, 120)
    }

    private val visibilityCheck = Runnable { updateVisibility() }

    fun updateVisibility() {
        if (!added) return
        val show = busy || !prefs.autoHide || focusedEditable() != null
        if (show != shown) {
            shown = show
            bubble.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        for (w in windows) {
            val n = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
            if (n.isEditable) return n
        }
        return null
    }

    // ---- touch: drag to move, tap to start/stop, long-press to cancel or open settings ----
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var downAt = 0L; var dragging = false
        bubble.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    downAt = System.currentTimeMillis(); dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        wm.updateViewLayout(bubble, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge()
                    else if (System.currentTimeMillis() - downAt > 600) onLongPress()
                    else onTap()
                }
            }
            true
        }
    }

    private fun snapToEdge() {
        val w = resources.displayMetrics.widthPixels
        params.x = if (params.x + params.width / 2 < w / 2) 0 else w - params.width
        params.y = params.y.coerceIn(0, resources.displayMetrics.heightPixels - params.height)
        wm.updateViewLayout(bubble, params)
        prefs.bubbleX = params.x; prefs.bubbleY = params.y
    }

    private fun onLongPress() {
        if (bubble.mode == BubbleView.Mode.RECORDING) {
            Engine.cancel(); setIdle(); toast("Cancelled")
        } else if (!busy) {
            openApp()
        }
    }

    private fun onTap() {
        when (bubble.mode) {
            BubbleView.Mode.RECORDING -> stopAndInsert()
            BubbleView.Mode.PROCESSING -> {}
            else -> start()
        }
    }

    private fun start() {
        if (!ModelManager.isReady(this)) { toast("Open wizpor to download the speech model"); openApp(); return }
        if (!MicService.running) MicService.start(this)
        target = focusedEditable()
        if (!Engine.startRecording()) {
            setError("Microphone unavailable. Open wizpor to allow it.")
            return
        }
        busy = true
        bubble.mode = BubbleView.Mode.RECORDING
        buzz()
        Engine.preload(this)
    }

    private fun stopAndInsert() {
        val samples = Engine.stopRecording()
        bubble.mode = BubbleView.Mode.PROCESSING
        buzz()
        if (Engine.isSilentBlock(samples)) {
            busy = false
            setError("Android blocked the mic. Open wizpor once, then try again.")
            return
        }
        thread(name = "wizpor-asr") {
            val res = runCatching { Engine.transcribe(this, samples) }
            main.post {
                busy = false
                res.onSuccess { r ->
                    Log.i("wizpor", "Transcribed in ${r.ms} ms: ${r.raw}")
                    if (r.text.isEmpty()) { toast("No speech heard"); setIdle() }
                    else { insert(r.text); setIdle() }
                }.onFailure {
                    Log.e("wizpor", "transcribe failed", it)
                    setError("Transcription failed: ${it.message}")
                }
            }
        }
    }

    private fun insert(text: String) {
        val node = target?.takeIf { it.refresh() && it.isEditable } ?: focusedEditable()
        if (node == null) { copy(text); toast("Copied. No text box was selected, so paste it."); return }
        val existing = if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) "" else node.text?.toString() ?: ""
        val r = TextInsert.insert(existing, node.textSelectionStart, node.textSelectionEnd, text)
        val ok = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, r.text)
            },
        )
        if (ok) {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, r.cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, r.cursor)
                },
            )
            return
        }
        // Some apps refuse SET_TEXT: fall back to the clipboard.
        copy(text)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) toast("Copied. Long-press the text box and paste.")
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("wizpor", text))
    }

    private fun setIdle() { busy = false; bubble.mode = BubbleView.Mode.IDLE; updateVisibility() }

    private fun setError(msg: String) {
        busy = false
        bubble.mode = BubbleView.Mode.ERROR
        toast(msg)
        main.postDelayed({ if (bubble.mode == BubbleView.Mode.ERROR) setIdle() }, 4000)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun buzz() {
        runCatching {
            getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(25, 120))
        }
    }

    companion object {
        @Volatile var instance: BubbleService? = null
    }
}
