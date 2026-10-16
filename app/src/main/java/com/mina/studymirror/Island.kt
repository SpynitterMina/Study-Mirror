package com.mina.studymirror

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * A small pill drawn around the camera cutout, in the style of a phone "island".
 * Tap it and it grows into a card; it also grows by itself at key moments.
 * It is drawn by the accessibility service, so it needs no extra permission.
 */
class Island(private val svc: AccessibilityService) {

    class Frame(
        val left: String,        // short label on the left of the camera
        val right: String,       // time on the right of the camera
        val progress: Float,     // 1 = full, 0 = empty
        val title: String,       // shown when expanded
        val sub: String,
        val accent: Int
    )

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ui = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var shape: GradientDrawable? = null
    private var compact: LinearLayout? = null
    private var expanded: LinearLayout? = null
    private var leftTv: TextView? = null
    private var rightTv: TextView? = null
    private var titleTv: TextView? = null
    private var timeTv: TextView? = null
    private var subTv: TextView? = null
    private var bar: View? = null

    private var anim: ValueAnimator? = null
    private var t = 0f                    // 0 = compact, 1 = expanded
    private var compW = 0
    private var compH = 0
    private var expW = 0
    private var expH = 0
    private var cutW = 0
    private var cutTop = 0
    private var cutBottom = 0
    private var last: Frame? = null
    private var message: Pair<String, String>? = null
    private var previewing = false
    private val collapseRun = Runnable { message = null; animateTo(0f) }

    private fun dp(v: Int) = svc.dp(v)
    private fun scale(): Float = when (Cfg.islandSize(svc)) { 0 -> 0.72f; 2 -> 1.18f; else -> 1f }

    // -------------------------------------------------------------------- public

    fun update(f: Frame?) {
        if (previewing) return
        if (f == null || !Cfg.island(svc)) {
            hide()
            return
        }
        last = f
        if (root == null) {
            readCutout()
            build()
            showWindow()
        }
        apply(f)
    }

    /** Grow the island for a few seconds with a message. */
    fun pop(title: String, sub: String, ms: Long) {
        if (!Cfg.island(svc) || !Cfg.islandPop(svc) || last == null) return
        message = title to sub
        last?.let { apply(it) }
        ui.removeCallbacks(collapseRun)
        animateTo(1f)
        ui.postDelayed(collapseRun, ms)
    }

    fun preview(accent: Int) {
        hide()
        previewing = true
        readCutout()
        build()
        showWindow()
        val demo = Frame("Block 2/5", "24:10", 0.55f, "Anatomy: upper limb", "\u201CWhat stands in the way becomes the way.\u201D", accent)
        last = demo
        apply(demo)
        ui.postDelayed({ animateTo(1f) }, 500)
        ui.postDelayed({
            previewing = false
            hide()
        }, 6500)
    }

    fun rebuild() {
        val f = last
        hide()
        if (f != null) update(f)
    }

    fun hide() {
        ui.removeCallbacks(collapseRun)
        anim?.cancel()
        anim = null
        val r = root
        if (r != null) {
            try { wm.removeView(r) } catch (e: Exception) {}
        }
        root = null
        params = null
        t = 0f
        message = null
    }

    // ------------------------------------------------------------------ geometry

    private fun readCutout() {
        cutW = 0; cutTop = 0; cutBottom = 0
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val r = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
            if (r != null && !r.isEmpty) {
                cutW = r.width(); cutTop = r.top; cutBottom = r.bottom
                return
            }
        } catch (e: Exception) {}
        try {
            val dm = svc.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val r = dm.getDisplay(0)?.cutout?.boundingRectTop
            if (r != null && !r.isEmpty) {
                cutW = r.width(); cutTop = r.top; cutBottom = r.bottom
            }
        } catch (e: Exception) {}
    }

    private fun screenWidth(): Int = svc.resources.displayMetrics.widthPixels

    // ---------------------------------------------------------------------- views

    private fun bgColor(accent: Int): Int = when (Cfg.islandStyle(svc)) {
        1 -> Color.rgb(
            (Color.red(accent) * 0.30f + 10).toInt().coerceAtMost(255),
            (Color.green(accent) * 0.30f + 10).toInt().coerceAtMost(255),
            (Color.blue(accent) * 0.30f + 10).toInt().coerceAtMost(255)
        )
        2 -> Color.argb(214, 18, 26, 36)
        else -> Color.BLACK
    }

    private fun build() {
        val sc = scale()
        val frame = FrameLayout(svc)
        val g = GradientDrawable()
        g.setColor(Color.BLACK)
        if (Cfg.islandStyle(svc) == 2) g.setStroke(dp(1), 0x33FFFFFF)
        frame.background = g
        shape = g

        // compact: [label] [camera] [time]
        val comp = LinearLayout(svc)
        comp.orientation = LinearLayout.HORIZONTAL
        comp.gravity = Gravity.CENTER_VERTICAL
        comp.setPadding(dp(10), 0, dp(10), 0)
        val l = TextView(svc)
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * sc)
        l.typeface = Theme.sans(svc, 600)
        l.setTextColor(Color.argb(225, 255, 255, 255))
        l.gravity = Gravity.CENTER
        l.minWidth = dp(46)
        l.maxLines = 1
        val gap = View(svc)
        val r = TextView(svc)
        r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * sc)
        r.typeface = Theme.mono(svc, 700)
        r.gravity = Gravity.CENTER
        r.minWidth = dp(44)
        r.maxLines = 1
        comp.addView(l, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        comp.addView(gap, LinearLayout.LayoutParams(if (cutW > 0) cutW + dp(8) else dp(6), LinearLayout.LayoutParams.MATCH_PARENT))
        comp.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        frame.addView(comp, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        // expanded card
        val exp = LinearLayout(svc)
        exp.orientation = LinearLayout.VERTICAL
        val topPad = if (cutBottom > 0) max(0, cutBottom - max(0, cutTop - dp(4)) + dp(8)) else dp(14)
        exp.setPadding(dp(18), topPad, dp(18), dp(16))
        val row = LinearLayout(svc)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(svc)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * sc)
        title.typeface = Theme.sans(svc, 700)
        title.setTextColor(Color.WHITE)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        val time = TextView(svc)
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * sc)
        time.typeface = Theme.mono(svc, 700)
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(time, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        exp.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val sub = TextView(svc)
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * sc)
        sub.typeface = Typeface.create(Theme.sans(svc, 500), Typeface.ITALIC)
        sub.setTextColor(Color.argb(200, 255, 255, 255))
        sub.maxLines = 3
        sub.setPadding(0, dp(6), 0, dp(10))
        exp.addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val track = FrameLayout(svc)
        track.background = svc.fill(0x26FFFFFF, 3)
        val fillBar = View(svc)
        fillBar.pivotX = 0f
        track.addView(fillBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        exp.addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5)))
        exp.alpha = 0f
        exp.visibility = View.INVISIBLE
        frame.addView(exp, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        frame.setOnClickListener {
            ui.removeCallbacks(collapseRun)
            if (t > 0.5f) {
                message = null
                animateTo(0f)
            } else {
                animateTo(1f)
                ui.postDelayed(collapseRun, 8000L)
            }
        }

        root = frame
        compact = comp
        expanded = exp
        leftTv = l
        rightTv = r
        titleTv = title
        timeTv = time
        subTv = sub
        bar = fillBar
    }

    private fun showWindow() {
        val frame = root ?: return
        val comp = compact ?: return
        comp.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        compW = max(comp.measuredWidth, dp(120))
        // FIX: small island must hug the cutout — scale height always, not only when no cutout exists
        val sc = scale()
        compH = if (cutBottom > 0) (((cutBottom - cutTop) + dp(8)) * sc).toInt().coerceAtLeast(dp(22))
                else (dp(30) * sc).toInt()
        expW = min(screenWidth() - dp(24), dp(360))
        shape?.cornerRadius = compH / 2f

        val p = WindowManager.LayoutParams(
            compW, compH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = (if (cutBottom > 0) max(0, cutTop - dp(4)) else dp(8)) + dp(Cfg.islandNudge(svc))
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params = p
        try {
            frame.alpha = 0f
            wm.addView(frame, p)
            frame.animate().alpha(1f).setDuration(220).start()
        } catch (e: Exception) {
            root = null
            params = null
        }
    }

    private fun apply(f: Frame) {
        val frame = root ?: return
        shape?.setColor(bgColor(f.accent))
        leftTv?.text = f.left
        rightTv?.text = f.right
        rightTv?.setTextColor(f.accent)
        timeTv?.text = f.right
        timeTv?.setTextColor(f.accent)
        val m = message
        titleTv?.text = m?.first ?: f.title
        subTv?.text = m?.second ?: f.sub
        bar?.background = svc.fill(f.accent, 3)
        bar?.scaleX = f.progress.coerceIn(0f, 1f)

        // keep the compact pill wide enough for the text, without jitter
        if (t == 0f) {
            val comp = compact ?: return
            comp.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = max(comp.measuredWidth, dp(120))
            if (kotlin.math.abs(w - compW) > dp(3)) {
                compW = w
                params?.width = w
                try { wm.updateViewLayout(frame, params) } catch (e: Exception) {}
            }
        }
    }

    // ------------------------------------------------------------------ animation

    private fun animateTo(target: Float) {
        val exp = expanded ?: return
        // measure the card at its final width so the height is right
        exp.measure(MeasureSpec.makeMeasureSpec(expW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        expH = max(exp.measuredHeight, compH)
        anim?.cancel()
        val a = ValueAnimator.ofFloat(t, target)
        a.duration = 340
        a.interpolator = OvershootInterpolator(0.8f)
        a.addUpdateListener {
            t = it.animatedValue as Float
            applyT()
        }
        anim = a
        a.start()
    }

    private fun applyT() {
        val frame = root ?: return
        val p = params ?: return
        val tt = t
        p.width = (compW + (expW - compW) * tt).toInt()
        p.height = (compH + (expH - compH) * tt).toInt()
        shape?.cornerRadius = (compH / 2f) + (dp(26) - compH / 2f) * tt.coerceIn(0f, 1f)
        val ca = (1f - tt * 2.2f).coerceIn(0f, 1f)
        val ea = ((tt - 0.35f) / 0.65f).coerceIn(0f, 1f)
        compact?.alpha = ca
        expanded?.alpha = ea
        expanded?.visibility = if (ea <= 0f) View.INVISIBLE else View.VISIBLE
        try { wm.updateViewLayout(frame, p) } catch (e: Exception) {}
    }
}
