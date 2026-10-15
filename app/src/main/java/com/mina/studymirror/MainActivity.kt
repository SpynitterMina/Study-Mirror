package com.mina.studymirror

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private var accent = 0
    private val INK = Theme.INK
    private val SOFT = Theme.SOFT
    private val GREEN = Cfg.BREAK

    private lateinit var scroll: ScrollView
    private lateinit var link: TextView
    private lateinit var ring: RingView
    private lateinit var sub: TextView
    private lateinit var statBlocks: TextView
    private lateinit var statBlocked: TextView
    private lateinit var statUrges: TextView
    private lateinit var notes: TextView
    private lateinit var laptopInfo: TextView
    private lateinit var pairMsg: TextView
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

    // ------------------------------------------------------------------ building blocks

    private fun txt(t: String, size: Float, color: Int = INK, weight: Int = 500): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        v.typeface = Theme.sans(this, weight)
        return v
    }

    private fun gcard(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        c.background = glass(22)
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(12)
        c.layoutParams = p
        return c
    }

    private fun pill(label: String, primary: Boolean, small: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = if (small) 13f else 15f
        b.typeface = Theme.sans(this, 700)
        b.stateListAnimator = null
        b.elevation = 0f
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        if (primary) {
            b.setTextColor(Theme.DARK_ON_ACCENT)
            b.background = fill(accent, 20)
        } else {
            b.setTextColor(INK)
            b.background = glass(20, strong = true)
        }
        b.setPadding(dp(if (small) 14 else 18), dp(if (small) 8 else 11), dp(if (small) 14 else 18), dp(if (small) 8 else 11))
        b.setOnClickListener { onClick() }
        b.press()
        return b
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = Cfg.accent(this)
        window.statusBarColor = Color.parseColor("#080C10")
        window.navigationBarColor = Color.parseColor("#080C10")

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(40), dp(18), dp(28))
        scroll = ScrollView(this)
        scroll.addView(col)
        scroll.background = GlowDrawable(accent)
        setContentView(scroll)

        // ---- header
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(txt("Study Mirror", 26f, INK, 800))
        link = txt("", 13f, SOFT)
        titles.addView(link)
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(pill("Settings", false, small = true) { startActivity(Intent(this, SettingsActivity::class.java)) })
        col.addView(head)

        // ---- the circular timer
        val c1 = gcard()
        ring = RingView(this)
        val rp = LinearLayout.LayoutParams(dp(250), dp(250))
        rp.gravity = Gravity.CENTER_HORIZONTAL
        c1.addView(ring, rp)
        sub = txt("", 15f, INK, 600)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(10), 0, 0)
        c1.addView(sub, LinearLayout.LayoutParams(-1, -2))
        col.addView(c1)

        // ---- quote
        val q = gcard()
        val quote = txt("", 16f, INK, 500)
        quote.typeface = Typeface.create(Theme.sans(this, 500), Typeface.ITALIC)
        val who = txt("", 13f, accent, 700)
        who.setPadding(0, dp(8), 0, 0)
        fun newQuote() {
            val p = Quotes.random()
            quote.text = "\u201C${p.first}\u201D"
            who.text = "\u2014 ${p.second}"
        }
        newQuote()
        q.setOnClickListener { newQuote() }
        q.addView(quote)
        q.addView(who)
        q.addView(txt("tap for another", 11f, Theme.MUTED).apply { setPadding(0, dp(6), 0, 0) })
        col.addView(q)

        // ---- today
        val stats = LinearLayout(this)
        val sp = LinearLayout.LayoutParams(-1, -2)
        sp.topMargin = dp(12)
        stats.layoutParams = sp
        fun statTile(label: String): TextView {
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.gravity = Gravity.CENTER
            t.background = glass(18)
            t.setPadding(dp(8), dp(12), dp(8), dp(12))
            val n = TextView(this)
            n.typeface = Theme.mono(this, 700)
            n.textSize = 24f
            n.setTextColor(INK)
            n.gravity = Gravity.CENTER
            n.text = "0"
            t.addView(n)
            t.addView(txt(label, 11f, SOFT).apply { gravity = Gravity.CENTER })
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(4), 0, dp(4), 0)
            stats.addView(t, lp)
            return n
        }
        statBlocks = statTile("blocks today")
        statBlocked = statTile("escapes stopped")
        statUrges = statTile("urges ridden")
        col.addView(stats)

        // ---- setup checklist (disappears once everything is ready)
        setupCard = gcard()
        setupCard.addView(txt("Get ready", 16f, INK, 700))
        fun row(name: String, ok: () -> Boolean, fix: () -> Unit) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            val t = txt(name, 14f)
            val b = pill("Fix", true, small = true) { fix() }
            r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(b)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(8)
            setupCard.addView(r, lp)
            setupRows.add(Triple(t, b, ok))
        }
        row("Accessibility", { GuardService.instance != null }) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row("Run in background", { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) }) { allowBackground() }
        row("Notifications", { Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setupCard.addView(txt("On Oppo, also open Settings, Battery, Study Mirror and allow background activity and auto-launch.", 12f, Theme.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        col.addView(setupCard)

        // ---- laptop link
        val lc = gcard()
        lc.addView(txt("Laptop link", 16f, INK, 700))
        laptopInfo = txt("", 13f, SOFT)
        laptopInfo.setPadding(0, dp(6), 0, dp(4))
        lc.addView(laptopInfo)
        pairMsg = txt("", 13f, accent, 600)
        pairMsg.visibility = View.GONE
        pairMsg.setPadding(0, 0, 0, dp(6))
        lc.addView(pairMsg)
        val btns = LinearLayout(this)
        val b1 = pill("Scan the laptop QR", true) { scan() }
        val b2 = pill("Type the code", false) { manualPair() }
        val bp1 = LinearLayout.LayoutParams(0, -2, 1f)
        bp1.rightMargin = dp(6)
        val bp2 = LinearLayout.LayoutParams(0, -2, 1f)
        bp2.leftMargin = dp(6)
        btns.addView(b1, bp1)
        btns.addView(b2, bp2)
        lc.addView(btns)
        lc.addView(txt("On the laptop press Pair phone. Scanning with Google Lens works too: tap the link it shows, then Open Study Mirror.", 12f, Theme.MUTED).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(lc)

        // ---- rules
        val rc = gcard()
        rc.addView(txt("Apps", 16f, INK, 700))
        val seg = LinearLayout(this)
        seg.setPadding(0, dp(10), 0, dp(6))
        modeBlock = txt("Block selected", 14f, INK, 600)
        modeBlock.gravity = Gravity.CENTER
        modeBlock.setPadding(dp(8), dp(10), dp(8), dp(10))
        modeBlock.setOnClickListener { setMode(false) }
        modeAllow = txt("Allow only selected", 14f, INK, 600)
        modeAllow.gravity = Gravity.CENTER
        modeAllow.setPadding(dp(8), dp(10), dp(8), dp(10))
        modeAllow.setOnClickListener { setMode(true) }
        val m1 = LinearLayout.LayoutParams(0, -2, 1f)
        m1.rightMargin = dp(4)
        val m2 = LinearLayout.LayoutParams(0, -2, 1f)
        m2.leftMargin = dp(4)
        seg.addView(modeBlock, m1)
        seg.addView(modeAllow, m2)
        rc.addView(seg)
        hint = txt("", 12f, Theme.MUTED)
        rc.addView(hint)
        rules = LinearLayout(this)
        rules.orientation = LinearLayout.VERTICAL
        rc.addView(rules)
        col.addView(rc)

        // ---- parked notes
        val nc = gcard()
        nc.addView(txt("Parked notes", 16f, INK, 700))
        notes = txt("", 14f, SOFT)
        notes.setPadding(0, dp(6), 0, dp(8))
        nc.addView(notes)
        nc.addView(pill("Clear notes", false, small = true) { Prefs.clearNotes(this); refresh() })
        col.addView(nc)

        for (i in 0 until col.childCount) col.getChildAt(i).rise(50L * i, dp(14).toFloat())

        loadApps()
        setMode(Prefs.allowOnly(this))
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Cfg.accent(this) != accent) {
            recreate()
            return
        }
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
        val connected = s != null && s.lastContact > 0 && now - s.lastContact < 8000
        link.text = when {
            s == null -> "Accessibility is off"
            connected -> "\u25CF Laptop connected"
            else -> "\u25CB Waiting for the laptop"
        }
        link.setTextColor(if (connected) GREEN else SOFT)

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
                if (left >= 0) ring.set(if (s.focusDurMs > 0) left.toFloat() / s.focusDurMs else 0f, fmt(left), "Focus \u00B7 $label", accent)
                else ring.set(1f, "Focus", label, accent)
                sub.text = if (s.task.isNotEmpty()) s.task else "You're doing very well."
            }
        }
        statBlocks.text = Prefs.count(this, "blocks").toString()
        statBlocked.text = Prefs.count(this, "blocked").toString()
        statUrges.text = Prefs.count(this, "urges").toString()
        val n = Prefs.notes(this)
        notes.text = if (n.isEmpty()) "Nothing parked yet." else n.take(8).joinToString("\n") { "\u2022 $it" }

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

    // ------------------------------------------------------------------ rules UI

    private fun setMode(allow: Boolean) {
        Prefs.setAllowOnly(this, allow)
        fun style(v: TextView, on: Boolean) {
            v.setTextColor(if (on) Theme.DARK_ON_ACCENT else INK)
            v.background = if (on) fill(accent, 14) else glass(14)
        }
        style(modeBlock, !allow)
        style(modeAllow, allow)
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
            val body = LinearLayout(this)
            body.orientation = LinearLayout.VERTICAL
            body.visibility = if (expanded.contains(cat)) View.VISIBLE else View.GONE
            val head = LinearLayout(this)
            head.gravity = Gravity.CENTER_VERTICAL
            head.setPadding(0, dp(10), 0, dp(4))
            val title = txt("", 15f, INK, 700)
            fun count() = list.count { chosen.contains(it.first) }
            fun retitle() { title.text = (if (expanded.contains(cat)) "\u25BE " else "\u25B8 ") + "$cat  (${count()}/${list.size})" }
            retitle()
            title.setOnClickListener {
                if (!expanded.remove(cat)) expanded.add(cat)
                body.visibility = if (expanded.contains(cat)) View.VISIBLE else View.GONE
                retitle()
            }
            val all = pill(if (count() == list.size) "None" else "All", false, small = true) {
                if (count() == list.size) list.forEach { chosen.remove(it.first) } else list.forEach { chosen.add(it.first) }
                save(); buildRules()
            }
            head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(all)
            for ((pkg, name, _) in list) {
                val cb = CheckBox(this)
                cb.text = name
                cb.setTextColor(INK)
                cb.typeface = Theme.sans(this, 500)
                cb.buttonTintList = ColorStateList.valueOf(accent)
                cb.isChecked = chosen.contains(pkg)
                cb.setOnCheckedChangeListener { _, on ->
                    if (on) chosen.add(pkg) else chosen.remove(pkg)
                    save(); retitle()
                }
                body.addView(cb)
            }
            rules.addView(head)
            rules.addView(body)
        }
    }

    // ------------------------------------------------------------------ pairing

    /**
     * Understands every shape the laptop can show:
     *   http://192.168.1.8:8765/p?k=KEY          (what the QR holds, so any camera shows a link)
     *   studymirror://pair?h=192.168.1.8:8765&k=KEY   (older QR, and the Open in app button)
     */
    private fun parsePairing(raw: String): Pair<String, String>? {
        return try {
            val u = Uri.parse(raw.trim())
            val key = u.getQueryParameter("k") ?: return null
            val host = when (u.scheme) {
                "studymirror" -> u.getQueryParameter("h")
                "http", "https" -> u.authority
                else -> null
            } ?: return null
            if (key.isBlank() || host.isBlank()) null else host to key
        } catch (e: Exception) {
            null
        }
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        val pr = parsePairing(data.toString())
        setIntent(Intent())
        if (pr != null) pair(pr.first, pr.second)
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener {
                val raw = it.rawValue ?: ""
                val pr = parsePairing(raw)
                if (pr == null) setPairMsg("That QR is not a Study Mirror code. Press Pair phone on the laptop and scan the new one.", true)
                else pair(pr.first, pr.second)
            }
            .addOnFailureListener {
                setPairMsg("The scanner could not start (${it.message}). Use Type the code, or open the QR with Google Lens.", true)
            }
    }

    private fun manualPair() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), 0)
        val a = EditText(this)
        a.hint = "Laptop address, like 192.168.1.8:8765"
        a.setSingleLine()
        val k = EditText(this)
        k.hint = "Pairing key shown under the QR"
        k.setSingleLine()
        col.addView(a)
        col.addView(k)
        AlertDialog.Builder(this)
            .setTitle("Pair by typing")
            .setView(col)
            .setPositiveButton("Pair") { _, _ ->
                var host = a.text.toString().trim()
                if (host.isNotEmpty() && !host.contains(":")) host += ":8765"
                val key = k.text.toString().trim()
                if (host.isEmpty() || key.isEmpty()) setPairMsg("Fill in both the address and the key.", true)
                else pair(host, key)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setPairMsg(msg: String, bad: Boolean) {
        pairMsg.text = msg
        pairMsg.setTextColor(if (bad) Cfg.swatches[3].second else accent)
        pairMsg.visibility = View.VISIBLE
    }

    private fun pair(host: String, key: String) {
        val svc = GuardService.instance
        if (svc == null) {
            setPairMsg("Turn on Study Mirror in Accessibility first, then pair again.", true)
            return
        }
        setPairMsg("Linking with $host ...", false)
        val port = if (svc.port != 0) svc.port else GuardService.PORT
        val token = Prefs.token(this)
        thread {
            var ok = false
            var msg = ""
            try {
                val hostName = host.substringBefore(":")
                val hostPort = host.substringAfter(":", "8765").toIntOrNull() ?: 8765
                // our own address on the route to the laptop
                val me = Socket().use { s ->
                    s.connect(InetSocketAddress(hostName, hostPort), 4000)
                    s.localAddress.hostAddress ?: ""
                }
                val url = URL("http://$hostName:$hostPort/pair?k=${Uri.encode(key)}&ip=$me&port=$port&t=$token")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                val code = c.responseCode
                c.disconnect()
                when (code) {
                    200 -> ok = true
                    403 -> msg = "The laptop says this code expired. Press Pair phone on the laptop again."
                    else -> msg = "The laptop answered with error $code."
                }
            } catch (e: SocketTimeoutException) {
                msg = "The laptop did not answer. Windows Firewall may be blocking it: open the help under the QR on the laptop."
            } catch (e: java.net.ConnectException) {
                msg = "The laptop refused the connection. Is the Study Mirror desktop app open and showing the QR?"
            } catch (e: Exception) {
                msg = "Could not reach the laptop (${e.message}). Are both on the same Wi-Fi?"
            }
            runOnUiThread {
                if (ok) {
                    Prefs.setLaptop(this, host)
                    setPairMsg("Linked with your laptop. You can close the QR window.", false)
                } else {
                    setPairMsg(msg, true)
                }
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
