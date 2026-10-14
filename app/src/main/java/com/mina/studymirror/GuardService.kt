package com.mina.studymirror

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The whole phone side lives here:
 *  - a tiny HTTP server the laptop page talks to
 *  - the session state (IDLE / FOCUS / BREAK)
 *  - the screens drawn over other apps
 *  - blocking of "escape" apps while a session is running
 */
class GuardService : AccessibilityService() {

    enum class Mode { IDLE, FOCUS, BREAK }
    enum class Kind { MENU, ENDING, BACK, BLOCKED }

    companion object {
        var instance: GuardService? = null
        const val PORT = 8080
    }

    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var mode = Mode.IDLE
    @Volatile private var block = 1
    @Volatile private var total = 0
    private var breakEndAt = 0L
    private var endingShown = false
    private var lastBlockAt = 0L

    private var overlay: View? = null
    private var overlayKind: Kind? = null
    private var countdown: TextView? = null
    private var server: ServerSocket? = null
    @Volatile var port = 0
    @Volatile var serverError = "not started yet"

    private val focusLines = listOf(
        "You're doing very good. Stay with it.",
        "This is a focus session, not a scroll session.",
        "The urge passes. The work stays. Keep going.",
        "Your future patients are counting on this block.",
        "One block at a time. That's all you need to do."
    )

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        startServer()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        ui.removeCallbacksAndMessages(null)
        removeOverlay()
        try { server?.close() } catch (e: Exception) {}
        server = null
        super.onDestroy()
    }

    // ------------------------------------------------------- escape-app blocking

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (mode == Mode.IDLE) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!Prefs.escape(this).contains(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (overlay == null) showBlocked()
    }

    // ----------------------------------------------------------------- commands

    /** Called from the HTTP thread or from MainActivity. Always runs the work on the main thread. */
    fun command(name: String, q: Map<String, String>): String {
        when (name) {
            "focus" -> {
                val b = q["block"]?.toIntOrNull() ?: 1
                val t = q["total"]?.toIntOrNull() ?: 0
                ui.post { cmdFocus(b, t) }
            }
            "break" -> {
                val secs = q["sec"]?.toIntOrNull() ?: ((q["min"]?.toIntOrNull() ?: 10) * 60)
                ui.post { cmdBreak(secs) }
            }
            "end" -> ui.post { cmdEnd() }
            "state" -> {}
            else -> return "{\"error\":\"unknown command\"}"
        }
        return stateJson()
    }

    private fun stateJson(): String {
        val left = if (mode == Mode.BREAK) ((breakEndAt - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0L) else 0L
        return "{\"mode\":\"$mode\",\"block\":$block,\"total\":$total,\"breakLeft\":$left}"
    }

    private fun label(n: Int) = if (total > 0) "Block $n of $total" else "Block $n"

    private fun cmdFocus(b: Int, t: Int) {
        ui.removeCallbacks(tick)
        removeOverlay()
        mode = Mode.FOCUS
        block = b
        total = t
        endingShown = false
        Toast.makeText(this, "Focus: ${label(block)}", Toast.LENGTH_SHORT).show()
        buzz()
    }

    private fun cmdBreak(seconds: Int) {
        ui.removeCallbacks(tick)
        mode = Mode.BREAK
        breakEndAt = SystemClock.elapsedRealtime() + seconds * 1000L
        endingShown = false
        showBreakMenu()
        buzz()
        ui.post(tick)
    }

    private fun cmdEnd() {
        ui.removeCallbacks(tick)
        removeOverlay()
        mode = Mode.IDLE
        endingShown = false
        Toast.makeText(this, "Session ended. Phone is free.", Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------- break timer

    private val tick = object : Runnable {
        override fun run() {
            if (mode != Mode.BREAK) return
            val left = breakEndAt - SystemClock.elapsedRealtime()
            if (left <= 0) {
                finishBreak()
                return
            }
            if (left <= 10_000 && !endingShown) showEnding()
            updateCountdown()
            ui.postDelayed(this, 500)
        }
    }

    private fun updateCountdown() {
        val left = (breakEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val secs = (left + 999) / 1000
        countdown?.text = if (overlayKind == Kind.ENDING) "$secs" else "%d:%02d".format(secs / 60, secs % 60)
    }

    private fun finishBreak() {
        mode = Mode.FOCUS
        block += 1
        endingShown = false
        buzz()
        val r = box(Color.parseColor("#14283A"))
        r.addView(tv("🎯", 56f))
        r.addView(tv("Back to ${label(block)}", 28f, bold = true))
        r.addView(tv(focusLines.random(), 18f))
        r.addView(btn("Let's go") { goHomeAndClose() })
        show(Kind.BACK, r)
        val v = overlay
        ui.postDelayed({ if (v != null && overlay === v) removeOverlay() }, 8000)
    }

    // ------------------------------------------------------------------ screens

    private fun showBreakMenu() {
        val r = box(Color.parseColor("#1F5A43"))
        r.addView(tv("🌿 Break time", 30f, bold = true))
        val cd = tv("", 44f, bold = true)
        r.addView(cd)

        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val tipText = tv("", 20f)
        tip.addView(tipText)
        tip.addView(btn("Got it, phone away") { goHomeAndClose() })

        fun choose(title: String, msg: String) {
            menu.visibility = View.GONE
            tipText.text = "$title\n\n$msg"
            tip.visibility = View.VISIBLE
        }

        menu.addView(tv("What do you want to do?", 18f))
        menu.addView(btn("🚶 Walk") {
            choose("🚶 Walk", "Phone in your pocket. Look at far things and breathe slowly. Try it without audio.")
        })
        menu.addView(btn("🍎 Snack") {
            choose("🍎 Snack", "Something light and some water. Eat slowly, away from any screen.")
        })
        menu.addView(btn("🎵 Music") { openMusic() })
        menu.addView(btn("💧 Water and stretch") {
            choose("💧 Water and stretch", "A glass of water, then shoulders, neck and back. Stand up tall.")
        })
        menu.addView(btn("😌 Rest my eyes") {
            choose("😌 Rest my eyes", "Look out of a window at something far away. Close your eyes for a minute.")
        })

        val mp = LinearLayout.LayoutParams.MATCH_PARENT
        val wc = LinearLayout.LayoutParams.WRAP_CONTENT
        r.addView(menu, LinearLayout.LayoutParams(mp, wc))
        r.addView(tip, LinearLayout.LayoutParams(mp, wc))
        r.addView(tv("Instagram, TikTok and the rest stay closed until your break ends.", 14f, Color.parseColor("#C8E6C9")))

        show(Kind.MENU, r)
        countdown = cd
        updateCountdown()
    }

    private fun showEnding() {
        endingShown = true
        val r = box(Color.parseColor("#14283A"))
        r.addView(tv("⏳", 52f))
        r.addView(tv("Break is ending", 28f, bold = true))
        val cd = tv("", 64f, bold = true)
        r.addView(cd)
        r.addView(tv("Time to go back. ${label(block + 1)} is waiting.\nYou've got this.", 18f))
        r.addView(btn("I'm ready") { goHomeAndClose() })
        show(Kind.ENDING, r)
        countdown = cd
        updateCountdown()
        buzz()
    }

    private fun showBlocked() {
        val r = box(Color.parseColor("#14283A"))
        if (mode == Mode.BREAK) {
            r.addView(tv("🌿", 56f))
            r.addView(tv("Not during your break", 26f, bold = true))
            r.addView(tv("Breaks are for recharging, not scrolling. Walk, snack, or put on some music.", 17f))
            r.addView(btn("OK") { goHomeAndClose() })
        } else {
            r.addView(tv("🎯", 56f))
            r.addView(tv("You're in ${label(block)}", 26f, bold = true))
            r.addView(tv(focusLines.random(), 17f))
            r.addView(btn("Back to focus") { goHomeAndClose() })
        }
        show(Kind.BLOCKED, r)
        val v = overlay
        ui.postDelayed({ if (v != null && overlay === v) removeOverlay() }, 6000)
    }

    private fun openMusic() {
        val apps = listOf("com.spotify.music", "com.anghami", "com.google.android.apps.youtube.music")
        for (p in apps) {
            val i = packageManager.getLaunchIntentForPackage(p) ?: continue
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                removeOverlay()
                return
            } catch (e: Exception) {}
        }
        Toast.makeText(this, "No music app found", Toast.LENGTH_SHORT).show()
    }

    // ----------------------------------------------------------- overlay helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(color)
        setPadding(dp(28), dp(48), dp(28), dp(48))
    }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }

    private fun btn(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTextColor(Color.parseColor("#14283A"))
        background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(Color.parseColor("#E8F1EC"))
        }
        setPadding(dp(20), dp(14), dp(20), dp(14))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun show(kind: Kind, v: View) {
        removeOverlay()
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            v.isFocusableInTouchMode = true
            v.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                    goHomeAndClose()
                    true
                } else {
                    false
                }
            }
            wm.addView(v, lp)
            v.requestFocus()
            overlay = v
            overlayKind = kind
        } catch (e: Exception) {
            Toast.makeText(this, "Could not show screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeOverlay() {
        val v = overlay
        if (v != null) {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (e: Exception) {}
        }
        overlay = null
        overlayKind = null
        countdown = null
    }

    private fun goHomeAndClose() {
        removeOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    // -------------------------------------------------------------- HTTP server

    fun ensureServer() {
        if (server == null) startServer()
    }

    private fun startServer() {
        if (server != null) return
        thread(isDaemon = true) {
            var found: ServerSocket? = null
            var lastErr = ""
            for (p in 8080..8089) {
                try {
                    val t = ServerSocket()
                    t.reuseAddress = true
                    t.bind(InetSocketAddress(p))
                    found = t
                    port = p
                    break
                } catch (e: Exception) {
                    lastErr = "port $p: ${e.message}"
                }
            }
            val sock = found
            if (sock == null) {
                serverError = lastErr
                return@thread
            }
            server = sock
            serverError = ""
            try {
                while (!sock.isClosed) {
                    val c = sock.accept()
                    thread(isDaemon = true) { handle(c) }
                }
            } catch (e: Exception) {
                serverError = "stopped: ${e.message}"
            }
            server = null
            port = 0
        }
    }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 3000
            val reader = c.getInputStream().bufferedReader()
            val first = reader.readLine() ?: return
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) line = reader.readLine()

            val parts = first.split(" ")
            val method = parts.getOrElse(0) { "" }
            val target = parts.getOrElse(1) { "/" }
            val path = target.substringBefore("?")
            val q = HashMap<String, String>()
            target.substringAfter("?", "").split("&").forEach { kv ->
                if (kv.contains("=")) q[kv.substringBefore("=")] = kv.substringAfter("=")
            }

            var status = "200 OK"
            var body = ""
            if (method == "OPTIONS") {
                status = "204 No Content"
            } else if (path == "/ping") {
                body = "{\"ok\":true}"
            } else if (q["t"] != Prefs.token(this)) {
                status = "403 Forbidden"
                body = "{\"error\":\"wrong code\"}"
            } else {
                body = command(path.trimStart('/'), q)
            }

            val bytes = body.toByteArray()
            val head = "HTTP/1.1 $status\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Private-Network: true\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            val out = c.getOutputStream()
            out.write(head.toByteArray())
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
        } finally {
            try { c.close() } catch (e: Exception) {}
        }
    }
}
