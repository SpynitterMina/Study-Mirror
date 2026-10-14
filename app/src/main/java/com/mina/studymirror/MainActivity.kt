package com.mina.studymirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.code.GmsBarcodeScannerOptions
import com.google.android.gms.code.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private val INK = Color.parseColor("#EAF2EE")
    private val MUTE = Color.parseColor("#9FB4C4")
    private val GREEN = Color.parseColor("#7FD1AE")
    private val BLUE = Color.parseColor("#7AA7FF")

    private lateinit var link: TextView
    private lateinit var ring: RingView
    private lateinit var sub: TextView
    private lateinit var stats: TextView
    private lateinit var notes: TextView
    private lateinit var laptopInfo: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var rules: LinearLayout
    private lateinit var hint: TextView
    private lateinit var modeBlock: TextView
    private lateinit var modeAllow: TextView
    private val setupRows = ArrayList<Triple<TextView, Button, () -> Boolean>>()
    private val expanded = HashSet<String>()
    private var apps: List<Triple<String, String, String>> = emptyList()

    private val tick = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#15242F")); setStroke(dp(1), Color.parseColor("#243847"))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(40), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(txt("Study Mirror", 26f, bold = true))
        link = txt("", 13f, MUTE)
        col.addView(link)

        // --- the circular timer
        val c1 = card()
        ring = RingView(this)
        c1.addView(ring, LinearLayout.LayoutParams(dp(250), dp(250)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        sub = txt("", 16f).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        c1.addView(sub, LinearLayout.LayoutParams(-1, -2))
        col.addView(c1)

        // --- quote
        val q = card()
        val quote = txt("", 16f).apply { setTypeface(Typeface.SERIF, Typeface.ITALIC) }
        val who = txt("", 13f, GREEN).apply { setPadding(0, dp(6), 0, 0) }
        fun newQuote() { val p = Quotes.random(); quote.text = "\u201C${p.first}\u201D"; who.text = "\u2014 ${p.second}" }
        newQuote(); q.setOnClickListener { newQuote() }
        q.addView(quote); q.addView(who); col.addView(q)

        stats = txt("", 14f, GREEN).apply { setPadding(0, dp(12), 0, 0) }
        col.addView(stats)

        // --- setup checklist (disappears once everything is ready)
        setupCard = card()
        setupCard.addView(txt("Get ready", 16f, bold = true))
        fun row(name: String, ok: () -> Boolean, fix: () -> Unit) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val t = txt(name, 15f)
            val b = btn("Fix") { fix() }
            r.addView(t, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(b)
            setupCard.addView(r)
            setupRows.add(Triple(t, b, ok))
        }
        row("Accessibility", { GuardService.instance != null }) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row("Run in background", { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) }) { allowBackground() }
        row("Notifications", { Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setupCard.addView(txt("On Oppo, also open Settings, Battery, Study Mirror and allow background activity and auto-launch.", 12f, MUTE).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(setupCard)

        // --- laptop link
        val lc = card()
        lc.addView(txt("Laptop link", 16f, bold = true))
        laptopInfo = txt("", 13f, MUTE).apply { setPadding(0, dp(6), 0, dp(6)) }
        lc.addView(laptopInfo)
        lc.addView(btn("Scan QR from the laptop") { scan() })
        col.addView(lc)

        // --- rules
        val rc = card()
        rc.addView(txt("Apps", 16f, bold = true))
        val seg = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(6)) }
        modeBlock = txt("Block selected", 14f).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10)); setOnClickListener { setMode(false) } }
        modeAllow = txt("Allow only selected", 14f).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10)); setOnClickListener { setMode(true) } }
        seg.addView(modeBlock, LinearLayout.LayoutParams(0, -2, 1f)); seg.addView(modeAllow, LinearLayout.LayoutParams(0, -2, 1f))
        rc.addView(seg)
        hint = txt("", 12f, MUTE); rc.addView(hint)
        rules = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rc.addView(rules)
        col.addView(rc)

        notes = txt("", 14f, MUTE).apply { setPadding(0, dp(14), 0, dp(6)) }
        col.addView(notes)
        col.addView(btn("Clear parked notes") { Prefs.clearNotes(this); refresh() })

        loadApps()
        setMode(Prefs.allowOnly(this))
    }

    override fun onResume() {
        super.onResume()
        GuardService.instance?.ensureServer()
        h.post(tick)
    }

    override fun onPause() { h.removeCallbacks(tick); super.onPause() }

    // ------------------------------------------------------------------ status
    private fun fmt(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private fun refresh() {
        val s = GuardService.instance
        val now = SystemClock.elapsedRealtime()
        link.text = when {
            s == null -> "Accessibility is off"
            s.lastContact > 0 && now - s.lastContact < 8000 -> "\u25CF Laptop connected"
            else -> "\u25CB Waiting for the laptop"
        }
        link.setTextColor(if (s != null && s.lastContact > 0 && now - s.lastContact < 8000) GREEN else MUTE)

        val label = if (s != null && s.total > 0) "Block ${s.block} of ${s.total}" else "Block ${s?.block ?: 1}"
        when {
            s == null || s.mode == GuardService.Mode.IDLE -> {
                ring.set(0f, "Free", "No session running", GREEN)
                sub.text = "Your laptop starts and ends sessions. Nothing to do here."
            }
            s.mode == GuardService.Mode.BREAK -> {
                val left = s.leftMs()
                ring.set(if (s.breakDurMs > 0) left.toFloat() / s.breakDurMs else 0f, fmt(left), "Break \u00B7 next is block ${s.block + 1}", GREEN)
                val pick = Prefs.breakChoice(this)
                sub.text = if (pick.isNotEmpty()) "You chose: $pick" else "Pick what to do on the break screen."
            }
            else -> {
                val left = s.leftMs()
                if (left >= 0) ring.set(if (s.focusDurMs > 0) left.toFloat() / s.focusDurMs else 0f, fmt(left), "Focus \u00B7 $label", BLUE)
                else ring.set(1f, "Focus", label, BLUE)
                sub.text = if (s.task.isNotEmpty()) s.task else "You're doing very well."
            }
        }
        stats.text = "Today: ${Prefs.count(this, "blocks")} blocks \u00B7 ${Prefs.count(this, "blocked")} escapes stopped \u00B7 " +
            "${Prefs.count(this, "urges")} urges ridden out"
        val n = Prefs.notes(this)
        notes.text = if (n.isEmpty()) "Parked notes: none yet" else "Parked notes\n" + n.take(8).joinToString("\n") { "\u2022 $it" }

        var allOk = true
        for ((t, b, ok) in setupRows) {
            val good = ok()
            if (!good) allOk = false
            t.setTextColor(if (good) GREEN else INK)
            t.text = (if (good) "\u2713 " else "\u2022 ") + t.text.toString().trimStart('\u2713', '\u2022', ' ')
            b.visibility = if (good) View.GONE else View.VISIBLE
        }
        setupCard.visibility = if (allOk) View.GONE else View.VISIBLE

        val lap = Prefs.laptop(this)
        laptopInfo.text = (if (lap.isNotEmpty()) "Linked with $lap\n" else "Not linked yet.\n") +
            "Manual way: code ${Prefs.token(this)} at ${addresses().joinToString(" or ") { "$it:${if (s != null && s.port != 0) s.port else GuardService.PORT}" }}"
    }

    private fun allowBackground() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ----------------------------------------------------------------- rules UI
    private fun setMode(allow: Boolean) {
        Prefs.setAllowOnly(this, allow)
        fun style(v: TextView, on: Boolean) {
            v.setTextColor(if (on) Color.parseColor("#07121A") else INK)
            v.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(if (on) GREEN else Color.parseColor("#0F1B26"))
            }
        }
        style(modeBlock, !allow); style(modeAllow, allow)
        hint.text = if (allow) "Tick the only apps you want open. Everything else closes. Phone, keyboard and core system apps always stay available."
        else "Tick the apps to keep closed during focus and breaks."
        buildRules()
    }

    private fun catOf(pkg: String, cat: Int): String {
        fun has(vararg k: String) = k.any { pkg.contains(it) }
        return when {
            has("instagram", "musically", "trill", "whatsapp", "facebook", "snapchat", "twitter", "telegram", "discord", "reddit", "pinterest", "linkedin", "messenger", "threads") -> "Social"
            has("youtube", "netflix", "spotify", "anghami", "twitch", "shahid", "soundcloud", "deezer") -> "Video & Music"
            has("chrome", "browser", "firefox", "opera", "brave") -> "Browsers"
            cat == ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_AUDIO -> "Video & Music"
            cat == ApplicationInfo.CATEGORY_GAME -> "Games"
            cat == ApplicationInfo.CATEGORY_NEWS -> "News"
            cat == ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Study & Work"
            else -> "Other"
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(launcher, 0)
            .map { Triple(it.activityInfo.packageName, it.loadLabel(pm).toString(), catOf(it.activityInfo.packageName, it.activityInfo.applicationInfo.category)) }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun buildRules() {
        rules.removeAllViews()
        val allow = Prefs.allowOnly(this)
        val chosen = (if (allow) Prefs.allowed(this) else Prefs.escape(this)).toMutableSet()
        fun save() { if (allow) Prefs.setAllowed(this, chosen) else Prefs.setEscape(this, chosen) }
        val order = listOf("Social", "Video & Music", "Games", "News", "Browsers", "Study & Work", "Other")
        for (cat in order) {
            val list = apps.filter { it.third == cat }
            if (list.isEmpty()) continue
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (expanded.contains(cat)) View.VISIBLE else View.GONE }
            val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
            val title = txt("", 15f, bold = true)
            fun count() = list.count { chosen.contains(it.first) }
            fun retitle() { title.text = (if (expanded.contains(cat)) "\u25BE " else "\u25B8 ") + "$cat  (${count()}/${list.size})" }
            retitle()
            title.setOnClickListener {
                if (!expanded.remove(cat)) expanded.add(cat)
                body.visibility = if (expanded.contains(cat)) View.VISIBLE else View.GONE
                retitle()
            }
            val all = btn(if (count() == list.size) "None" else "All") {
                if (count() == list.size) list.forEach { chosen.remove(it.first) } else list.forEach { chosen.add(it.first) }
                save(); buildRules()
            }
            head.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); head.addView(all)
            for ((pkg, name, _) in list) {
                val cb = CheckBox(this)
                cb.text = name; cb.setTextColor(INK); cb.isChecked = chosen.contains(pkg)
                cb.setOnCheckedChangeListener { _, on ->
                    if (on) chosen.add(pkg) else chosen.remove(pkg)
                    save(); retitle()
                }
                body.addView(cb)
            }
            rules.addView(head); rules.addView(body)
        }
    }

    // ------------------------------------------------------------- QR pairing
    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { pair(it.rawValue ?: "") }
            .addOnFailureListener { Toast.makeText(this, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    /** QR content: studymirror://pair?h=<laptop ip:port>&k=<key>. We tell the laptop who we are. */
    private fun pair(raw: String) {
        val u = Uri.parse(raw)
        val host = u.getQueryParameter("h")
        val key = u.getQueryParameter("k")
        if (u.scheme != "studymirror" || host == null || key == null) {
            Toast.makeText(this, "That is not a Study Mirror code", Toast.LENGTH_LONG).show(); return
        }
        val port = GuardService.instance?.port?.takeIf { it != 0 } ?: GuardService.PORT
        val prefix = host.substringBefore(":").substringBeforeLast(".")
        val mine = addresses().firstOrNull { it.startsWith("$prefix.") } ?: addresses().first()
        thread {
            val ok = try {
                val url = URL("http://$host/pair?k=${Uri.encode(key)}&ip=$mine&port=$port&t=${Prefs.token(this)}")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                val code = c.responseCode; c.disconnect(); code == 200
            } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) { Prefs.setLaptop(this, host); Toast.makeText(this, "Linked with your laptop", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Could not reach the laptop. Same Wi-Fi?", Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun addresses(): List<String> {
        val out = ArrayList<String>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces()
            if (ifs != null) for (nif in ifs) for (a in nif.inetAddresses)
                if (!a.isLoopbackAddress && a is Inet4Address) out.add(a.hostAddress ?: "")
        } catch (e: Exception) {}
        return if (out.isEmpty()) listOf("(connect to Wi-Fi)") else out
    }
}
