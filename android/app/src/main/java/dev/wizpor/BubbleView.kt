package dev.wizpor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** The floating mic bubble. Draws its own state; no layouts or images needed. */
class BubbleView(ctx: Context) : View(ctx) {
    enum class Mode { IDLE, RECORDING, PROCESSING, ERROR, LOADING }

    var mode = Mode.IDLE
        set(v) { field = v; invalidate() }
    var levelProvider: () -> Float = { 0f }

    private val dp = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()
    private var spin = 0f

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cx = w / 2
        val r = w / 2 - 6 * dp
        bg.color = when (mode) {
            Mode.IDLE -> Color.argb(235, 24, 24, 27)
            Mode.RECORDING -> Color.rgb(239, 68, 68)
            Mode.PROCESSING, Mode.LOADING -> Color.rgb(245, 158, 11)
            Mode.ERROR -> Color.rgb(127, 29, 29)
        }
        if (mode == Mode.RECORDING) {
            val lvl = (levelProvider() * 10f).coerceIn(0f, 1f)
            ring.color = Color.argb((80 + 120 * lvl).toInt(), 239, 68, 68)
            ring.strokeWidth = 2 * dp + 4 * dp * lvl
            c.drawCircle(cx, cx, r + 2 * dp * lvl, ring)
        }
        c.drawCircle(cx, cx, r, bg)

        if (mode == Mode.PROCESSING || mode == Mode.LOADING) {
            stroke.strokeWidth = 3 * dp
            rect.set(cx - r * 0.5f, cx - r * 0.5f, cx + r * 0.5f, cx + r * 0.5f)
            c.drawArc(rect, spin, 270f, false, stroke)
            spin = (spin + 12f) % 360f
            postInvalidateOnAnimation()
            return
        }
        if (mode == Mode.RECORDING) {
            // Stop square
            val s = r * 0.32f
            rect.set(cx - s, cx - s, cx + s, cx + s)
            c.drawRoundRect(rect, 3 * dp, 3 * dp, fg)
            postInvalidateOnAnimation()
            return
        }
        // Mic glyph
        val mw = r * 0.28f
        val mh = r * 0.55f
        rect.set(cx - mw, cx - mh * 0.85f, cx + mw, cx + mh * 0.35f)
        c.drawRoundRect(rect, mw, mw, fg)
        stroke.strokeWidth = 2.2f * dp
        path.reset()
        rect.set(cx - mw * 1.8f, cx - mh * 0.55f, cx + mw * 1.8f, cx + mh * 0.6f)
        path.addArc(rect, 20f, 140f)
        c.drawPath(path, stroke)
        c.drawLine(cx, cx + mh * 0.6f, cx, cx + mh * 0.85f, stroke)
        if (mode == Mode.ERROR) {
            fg.color = Color.rgb(252, 165, 165)
            c.drawCircle(cx + r * 0.62f, cx - r * 0.62f, 5 * dp, fg)
            fg.color = Color.WHITE
        }
    }
}
