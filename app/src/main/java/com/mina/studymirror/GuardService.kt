package com.mina.studymirror

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ApplicationInfo
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
import android.widget.EditText
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
    enum class Kind { MENU, ENDING, BACK, BLOCKED, URGE, EXTEND }

    companion object {
        var instance: GuardService? = null
        const val PORT = 8080
    }

    private val ui = Handler(Looper.getMainLooper())

    @Volatile var mode = Mode.IDLE
    @Volatile var block = 1
    @Volatile var total = 0
    @Volatile var breakEndAt = 0L
    @Volatile var breakDurMs = 0L
    @Volatile var focusEndAt = 0L
    @Volatile var focusDurMs = 0L
    @Volatile var task = ""
    @Volatile var lastContact = 0L
    private var focusStartWall = 0L
    private var endingShown = false
    private var lastBlockAt = 0L
    private var extUsed = false

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
        val ch = NotificationChannel("session", "Study session", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startServer()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        ui.removeCallbacksAndMessages(null)
        try { getSystemService(NotificationManager::class.java).cancel(1) } catch (e: Exception) {}
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
        if (!isBlocked(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        Prefs.bump(this, "blocked")
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (overlay == null) showBlocked()
    }

    private val breakApps = setOf("com.spotify.music", "com.anghami", "com.google.android.apps.youtube.music")

    private fun isBlocked(pkg: String): Boolean {
        if (mode == Mode.BREAK && breakApps.contains(pkg)) return false
        if (!Prefs.allowOnly(this)) return Prefs.escape(this).contains(pkg)
        return !Prefs.allowed(this).contains(pkg) && !isEssential(pkg)
    }

    /** Core system apps (phone, keyboard, launcher, system UI) are never closed in allow-only mode. */
    private fun isEssential(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")) return true
        return try {
            val f = packageManager.getApplicationInfo(pkg, 0).flags
            (f and ApplicationInfo.FLAG_SYSTEM) != 0 && (f and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        } catch (e: Exception) { true }
    }

    // ------------------------------------------------------------ notification
    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtime()
        when (mode) {
            Mode.IDLE -> nm.cancel(1)
            Mode.BREAK -> {
                val pick = Prefs.breakChoice(this)
                post("Break" + if (pick.isNotEmpty()) " \u00B7 $pick" else "", "Back to ${label(block + 1)} when it ends", wall + (breakEndAt - mono), true)
            }
            Mode.FOCUS -> {
                val line = if (task.isNotEmpty()) task else "You're doing very well"
                if (focusEndAt > 0) post("Focus \u00B7 ${label(block)}", line, wall + (focusEndAt - mono), true)
                else post("Focus \u00B7 ${label(block)}", line, focusStartWall, false)
            }
        }
    }

    private fun post(title: String, text: String, whenMs: Long, down: Boolean) {
        try {
            val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val b = Notification.Builder(this, "session")
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setWhen(whenMs).setUsesChronometer(true).setChronometerCountDown(down)
            getSystemService(NotificationManager::class.java).notify(1, b.build())
        } catch (e: Exception) {}
    }

    fun leftMs(): Long {
        val now = SystemClock.elapsedRealtime()
        return when {
            mode == Mode.BREAK -> (breakEndAt - now).coerceAtLeast(0L)
            mode == Mode.FOCUS && focusEndAt > 0 -> (focusEndAt - now).coerceAtLeast(0L)
            else -> -1L
        }
    }

    // ----------------------------------------------------------------- commands

    /** Called from the HTTP thread or from MainActivity. Always runs the work on the main thread. */
    fun command(name: String, q: Map<String, String>): String {
        when (name) {
            "focus" -> {
                val b = q["block"]?.toIntOrNull() ?: 1
                val t = q["total"]?.toIntOrNull() ?: 0
                val sec = q["sec"]?.toIntOrNull() ?: 0
                val tk = q["task"] ?: ""
                ui.post { cmdFocus(b, t, sec, tk) }
            }
            "break" -> {
                val secs = q["sec"]?.toIntOrNull() ?: ((q["min"]?.toIntOrNull() ?: 10) * 60)
                val endAt = SystemClock.elapsedRealtime() + secs * 1000L
                ui.post { cmdBreak(secs, endAt) }
                return "{\"ok\":true,\"leftMs\":${secs * 1000L}}"
            }
            "end" -> ui.post { cmdEnd() }
            "state" -> {}
            else -> return "{\"error\":\"unknown command\"}"
        }
        return stateJson()
    }

    private fun js(t: String) = t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private fun stateJson(): String {
        val left = if (mode == Mode.BREAK) ((breakEndAt - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0L) else 0L
        val leftMs = if (mode == Mode.BREAK) (breakEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else 0L
        val notes = Prefs.notes(this).take(6).joinToString(",") { "\"" + js(it) + "\"" }
        return "{\"mode\":\"$mode\",\"block\":$block,\"total\":$total,\"breakLeft\":$left,\"breakLeftMs\":$leftMs," +
            "\"stats\":{\"blocks\":${Prefs.count(this, "blocks")},\"blocked\":${Prefs.count(this, "blocked")}," +
            "\"urges\":${Prefs.count(this, "urges")},\"extensions\":${Prefs.count(this, "extensions")}}," +
            "\"notes\":[$notes]}"
    }

    private fun nowText() = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())

    private fun label(n: Int) = if (total > 0) "Block $n of $total" else "Block $n"

    private fun cmdFocus(b: Int, t: Int, sec: Int, tk: String) {
        ui.removeCallbacks(tick)
        removeOverlay()
        mode = Mode.FOCUS
        block = b
        total = t
        endingShown = false
        task = tk
        focusDurMs = sec * 1000L
        focusEndAt = if (sec > 0) SystemClock.elapsedRealtime() + sec * 1000L else 0L
        focusStartWall = System.currentTimeMillis()
        updateNotification()
        Toast.makeText(this, "Focus: ${label(block)}", Toast.LENGTH_SHORT).show()
        buzz()
    }

    private fun cmdBreak(seconds: Int, endAt: Long) {
        ui.removeCallbacks(tick)
        mode = Mode.BREAK
        extUsed = false
        Prefs.bump(this, "blocks")
        breakEndAt = endAt
        breakDurMs = seconds * 1000L
        Prefs.setBreakChoice(this, "")
        updateNotification()
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
        task = ""
        updateNotification()
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
        focusEndAt = 0L
        focusStartWall = System.currentTimeMillis()
        updateNotification()
        buzz()
        val r = box(Color.parseColor("#14283A"))
        r.addView(tv("🎯", 56f))
        r.addView(tv("Back to ${label(block)}", 28f, bold = true))
        r.addView(tv(Quotes.line(), 18f))
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
            Prefs.setBreakChoice(this, title.substringAfter(" "))
            updateNotification()
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
        if (!extUsed) r.addView(btn("I need 5 more minutes") { showExtend() })
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
            r.addView(tv(Quotes.line(), 17f))
            r.addView(btn("Back to focus") { goHomeAndClose() })
            r.addView(btn("I feel the urge to escape") { showUrge() })
        }
        show(Kind.BLOCKED, r)
        val v = overlay
        ui.postDelayed({ if (v != null && overlay === v) removeOverlay() }, 6000)
    }

    private fun showUrge() {
        Prefs.bump(this, "urges")
        val r = box(Color.parseColor("#2A2140"))
        r.addView(tv("🌊", 52f))
        r.addView(tv("The urge peaks, then it fades", 24f, bold = true))
        val cd = tv("3:00", 48f, bold = true)
        r.addView(cd)
        r.addView(tv("What are you feeling right now? One line is enough.", 16f))
        val et = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#9A90B8"))
            hint = "too much, scared I won't finish..."
            maxLines = 3
        }
        r.addView(et, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        r.addView(btn("Write it down, keep going") {
            val t = et.text.toString().trim()
            if (t.isNotEmpty()) Prefs.addNote(this, nowText() + "  " + t)
            goHomeAndClose()
        })
        show(Kind.URGE, r)
        val end = SystemClock.elapsedRealtime() + 180_000L
        val v = overlay
        val run = object : Runnable {
            override fun run() {
                if (overlay !== v) return
                val s = ((end - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0L)
                if (s > 0) {
                    cd.text = "%d:%02d".format(s / 60, s % 60)
                    ui.postDelayed(this, 500)
                } else {
                    cd.text = "You rode it out"
                }
            }
        }
        ui.post(run)
    }

    private fun showExtend() {
        val hold = 75_000L
        breakEndAt += hold
        endingShown = true
        val r = box(Color.parseColor("#14283A"))
        r.addView(tv("5 more minutes?", 26f, bold = true))
        r.addView(tv("Why do you need them? Be honest. The wait is part of the deal.", 16f))
        val et = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7F93A3"))
            hint = "my reason..."
            maxLines = 3
        }
        r.addView(et, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val go = btn("Wait 60s") {}
        go.isEnabled = false
        go.alpha = 0.5f
        go.setOnClickListener {
            val why = et.text.toString().trim()
            if (why.isEmpty()) {
                Toast.makeText(this, "Write a reason first", Toast.LENGTH_SHORT).show()
            } else {
                extUsed = true
                Prefs.bump(this, "extensions")
                Prefs.addNote(this, nowText() + "  extended break: " + why)
                breakEndAt += 300_000L - hold
                breakDurMs += 300_000L
                updateNotification()
                endingShown = false
                showBreakMenu()
            }
        }
        r.addView(go)
        r.addView(btn("Never mind, back to focus") {
            breakEndAt -= hold
            endingShown = false
            showEnding()
        })
        show(Kind.EXTEND, r)
        val end = SystemClock.elapsedRealtime() + 60_000L
        val v = overlay
        val run = object : Runnable {
            override fun run() {
                if (overlay !== v) return
                val s = ((end - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0L)
                if (s > 0) {
                    go.text = "Wait ${s}s"
                    ui.postDelayed(this, 500)
                } else {
                    go.text = "Give me 5 minutes"
                    go.isEnabled = true
                    go.alpha = 1f
                }
            }
        }
        ui.post(run)
    }

    private fun openMusic() {
        Prefs.setBreakChoice(this, "Music")
        updateNotification()
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
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            v.isFocusableInTouchMode = true
            v.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                    goHomeAndClose()
                    true
                } else {
                    false
                }
            }
            v.alpha = 0f
            wm.addView(v, lp)
            v.animate().alpha(1f).setDuration(400).start()
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
                if (kv.contains("=")) q[kv.substringBefore("=")] = java.net.URLDecoder.decode(kv.substringAfter("="), "UTF-8")
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
                lastContact = SystemClock.elapsedRealtime()
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
