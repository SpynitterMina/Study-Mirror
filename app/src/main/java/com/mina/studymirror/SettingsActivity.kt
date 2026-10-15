package com.mina.studymirror

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** One place to choose what kind of notifications appear, how they look, and which pictures to use. */
class SettingsActivity : Activity() {

    private val REQ_IMG = 77
    private var accent = 0
    private lateinit var imgBox: LinearLayout

    private fun txt(t: String, size: Float, color: Int = Theme.INK, weight: Int = 500): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        v.typeface = Theme.sans(this, weight)
        return v
    }

    private fun gcard(title: String, subtitle: String = ""): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        c.background = glass(22)
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(12)
        c.layoutParams = p
        c.addView(txt(title, 16f, Theme.INK, 700))
        if (subtitle.isNotEmpty()) c.addView(txt(subtitle, 12f, Theme.SOFT).apply { setPadding(0, dp(2), 0, dp(6)) })
        return c
    }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 14f
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
            b.setTextColor(Theme.INK)
            b.background = glass(20, strong = true)
        }
        b.setPadding(dp(16), dp(10), dp(16), dp(10))
        b.setOnClickListener { onClick() }
        b.press()
        val p = LinearLayout.LayoutParams(-2, -2)
        p.topMargin = dp(8)
        b.layoutParams = p
        return b
    }

    private fun applyNow() { GuardService.instance?.applyConfig() }

    private fun switchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(txt(title, 15f, Theme.INK, 600))
        left.addView(txt(desc, 12f, Theme.SOFT))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this)
        sw.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(accent, Theme.SOFT))
        sw.trackTintList = ColorStateList(states, intArrayOf(withAlpha(accent, 120), withAlpha(Color.WHITE, 50)))
        sw.setOnCheckedChangeListener { _, on ->
            onChange(on)
            applyNow()
        }
        row.addView(sw)
        return row
    }

    private fun segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val seg = LinearLayout(this)
        seg.setPadding(0, dp(6), 0, dp(6))
        val tiles = ArrayList<TextView>()
        fun paint(sel: Int) {
            for (i in tiles.indices) {
                tiles[i].setTextColor(if (i == sel) Theme.DARK_ON_ACCENT else Theme.INK)
                tiles[i].background = if (i == sel) fill(accent, 14) else glass(14)
            }
        }
        for ((i, name) in options.withIndex()) {
            val t = txt(name, 13f, Theme.INK, 600)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(6), dp(10), dp(6), dp(10))
            t.setOnClickListener {
                paint(i)
                onPick(i)
                applyNow()
            }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            seg.addView(t, lp)
            tiles.add(t)
        }
        paint(selected)
        return seg
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, suffix: String, onChange: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(6), 0, dp(4))
        val name = txt("$label: $value$suffix", 13f, Theme.SOFT)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value - min
        sb.progressTintList = ColorStateList.valueOf(accent)
        sb.thumbTintList = ColorStateList.valueOf(accent)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    onChange(p + min)
                    name.text = "$label: ${p + min}$suffix"
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { applyNow() }
        })
        box.addView(name)
        box.addView(sb)
        return box
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = Cfg.accent(this)
        window.statusBarColor = Color.parseColor("#080C10")
        window.navigationBarColor = Color.parseColor("#080C10")

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(40), dp(18), dp(28))
        val scroll = ScrollView(this)
        scroll.addView(col)
        scroll.background = GlowDrawable(accent)
        setContentView(scroll)

        // ---- header
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(txt("Settings", 26f, Theme.INK, 800), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(pill("Done", false) { finish() })
        col.addView(head)

        // ---- color
        val color = gcard("Color", "Used for the ring, buttons, notifications and the island.")
        val sw = LinearLayout(this)
        sw.setPadding(0, dp(8), 0, dp(4))
        for ((name, c) in Cfg.swatches) {
            val dot = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            if (c == accent) g.setStroke(dp(3), Color.WHITE)
            dot.background = g
            dot.contentDescription = name
            dot.setOnClickListener {
                Cfg.setAccent(this, c)
                applyNow()
                recreate()
            }
            dot.press()
            val lp = LinearLayout.LayoutParams(dp(38), dp(38))
            lp.rightMargin = dp(12)
            sw.addView(dot, lp)
        }
        color.addView(sw)
        col.addView(color)

        // ---- notifications
        val n = gcard("Notifications", "Choose every way the session can show up. Mix as many as you like.")
        n.addView(switchRow("Status card", "A timer in the notification shade.", Cfg.status(this)) { Cfg.setStatus(this, it) })
        n.addView(switchRow("Top-of-shade card", "A media-style card. Android keeps these above other notifications and shows them on the lock screen.", Cfg.media(this)) { Cfg.setMedia(this, it) })
        n.addView(switchRow("Live Update chip", "On Android 16 the session sits on the lock screen and in the status bar. Older versions ignore it.", Cfg.live(this)) { Cfg.setLive(this, it) })
        n.addView(switchRow("Alert banners", "Pop-down banners when a block or break starts or ends. They also show on the lock screen.", Cfg.alerts(this)) { Cfg.setAlerts(this, it) })
        n.addView(switchRow("Notch island", "A pill around the camera that grows at key moments. Tap it for the full card.", Cfg.island(this)) { Cfg.setIsland(this, it) })
        val nb = LinearLayout(this)
        nb.addView(pill("Send a test alert", true) {
            val s = GuardService.instance
            if (s == null) Toast.makeText(this, "Turn on Study Mirror in Accessibility first", Toast.LENGTH_LONG).show() else s.testAlert()
        })
        nb.addView(pill("Notification settings", false) {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        }.also { (it.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8) })
        n.addView(nb)
        col.addView(n)

        // ---- island
        val isl = gcard("Notch island", "Where it sits and how it looks.")
        isl.addView(txt("Size", 13f, Theme.SOFT))
        isl.addView(segmented(listOf("Small", "Medium", "Large"), Cfg.islandSize(this)) { Cfg.setIslandSize(this, it) })
        isl.addView(txt("Style", 13f, Theme.SOFT))
        isl.addView(segmented(listOf("Black", "Tinted", "Glass"), Cfg.islandStyle(this)) { Cfg.setIslandStyle(this, it) })
        isl.addView(slider("Move down", 0, 30, Cfg.islandNudge(this), " dp") { Cfg.setIslandNudge(this, it) })
        isl.addView(switchRow("Grow by itself", "Expands for a few seconds when a block or break starts or ends.", Cfg.islandPop(this)) { Cfg.setIslandPop(this, it) })
        isl.addView(pill("Preview the island", true) {
            val s = GuardService.instance
            if (s == null) Toast.makeText(this, "Turn on Study Mirror in Accessibility first", Toast.LENGTH_LONG).show() else s.previewIsland()
        })
        col.addView(isl)

        // ---- full screens
        val scr = gcard("Full screens", "The screens that cover other apps during a session.")
        scr.addView(switchRow("Blur what is behind", "Frosted glass over the app underneath. Needs Android 12 or newer.", Cfg.blur(this)) { Cfg.setBlur(this, it) })
        scr.addView(switchRow("Use my pictures", "Show one of your pictures behind these screens and on the notification card.", Cfg.useImages(this)) { Cfg.setUseImages(this, it) })
        scr.addView(slider("Darken pictures", 20, 90, Cfg.dim(this), "%") { Cfg.setDim(this, it) })
        col.addView(scr)

        // ---- pictures
        val pics = gcard("My pictures", "Add up to ${Backgrounds.MAX}. One is picked at random each time.")
        imgBox = LinearLayout(this)
        imgBox.orientation = LinearLayout.VERTICAL
        pics.addView(imgBox)
        pics.addView(pill("Add pictures", true) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(i, REQ_IMG)
        })
        col.addView(pics)
        buildImages()

        for (i in 0 until col.childCount) col.getChildAt(i).rise(50L * i, dp(14).toFloat())
    }

    private fun buildImages() {
        imgBox.removeAllViews()
        val files = Backgrounds.list(this)
        if (files.isEmpty()) {
            imgBox.addView(txt("No pictures yet.", 13f, Theme.MUTED))
            return
        }
        val total = resources.displayMetrics.widthPixels - dp(36 + 36)
        val cell = total / 3
        var row: LinearLayout? = null
        for ((i, f) in files.withIndex()) {
            if (i % 3 == 0) {
                row = LinearLayout(this)
                imgBox.addView(row)
            }
            val frame = FrameLayout(this)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = fill(0x22FFFFFF, 14)
            iv.clipToOutline = true
            iv.outlineProvider = ViewOutlineProvider.BACKGROUND
            iv.setImageBitmap(Backgrounds.decode(f, 360))
            frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val x = txt("\u2715", 13f, Color.WHITE, 700)
            x.gravity = Gravity.CENTER
            x.background = fill(0x99000000.toInt(), 12)
            x.setOnClickListener {
                Backgrounds.remove(this, f)
                buildImages()
            }
            frame.addView(x, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(4), 0) })
            val lp = LinearLayout.LayoutParams(cell - dp(8), cell - dp(8))
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            row?.addView(frame, lp)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMG || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<android.net.Uri>()
        val clip = data.clipData
        if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        else data.data?.let { uris.add(it) }
        Toast.makeText(this, "Adding ${uris.size} picture(s)...", Toast.LENGTH_SHORT).show()
        thread {
            var added = 0
            for (u in uris) if (Backgrounds.add(this, u)) added++
            runOnUiThread {
                buildImages()
                Toast.makeText(this, if (added == uris.size) "Added $added" else "Added $added of ${uris.size} (limit is ${Backgrounds.MAX})", Toast.LENGTH_SHORT).show()
                applyNow()
            }
        }
    }
}
