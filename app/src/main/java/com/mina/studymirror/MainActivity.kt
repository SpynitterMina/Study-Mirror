package com.mina.studymirror

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)

        col.addView(TextView(this).apply {
            text = "Study Mirror"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, pad / 2) }
        info = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, pad / 2) }
        col.addView(status)
        col.addView(info)

        col.addView(button("1. Turn on Study Mirror in Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("2. Battery settings (allow background)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        col.addView(button("Test: 45-second break") { test("break", mapOf("sec" to "45")) })
        col.addView(button("Test: focus block 2 of 5") { test("focus", mapOf("block" to "2", "total" to "5")) })
        col.addView(button("Test: end session") { test("end", emptyMap()) })

        col.addView(TextView(this).apply {
            text = "\nApps kept closed during focus and breaks"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val chosen = Prefs.escape(this).toMutableSet()
        for ((pkg, name) in apps) {
            val cb = CheckBox(this)
            cb.text = name
            cb.isChecked = chosen.contains(pkg)
            cb.setOnCheckedChangeListener { _, on ->
                if (on) chosen.add(pkg) else chosen.remove(pkg)
                Prefs.setEscape(this, chosen)
            }
            col.addView(cb)
        }
    }

    override fun onResume() {
        super.onResume()
        val on = GuardService.instance != null
        status.text = if (on) "Status: running" else "Status: not running. Do step 1 below."
        info.text = "Pairing code: ${Prefs.token(this)}\n\nPhone address (type this into the laptop page):\n" +
            addresses().joinToString("\n") { "$it:${GuardService.PORT}" }
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun test(cmd: String, params: Map<String, String>) {
        val s = GuardService.instance
        if (s == null) {
            Toast.makeText(this, "Turn on Study Mirror in Accessibility first", Toast.LENGTH_LONG).show()
            return
        }
        s.command(cmd, params)
        if (cmd == "break") moveTaskToBack(true)
    }

    private fun addresses(): List<String> {
        val out = ArrayList<String>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces()
            if (ifs != null) {
                for (nif in ifs) {
                    for (a in nif.inetAddresses) {
                        if (!a.isLoopbackAddress && a is Inet4Address) {
                            out.add(a.hostAddress ?: "")
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return if (out.isEmpty()) listOf("(connect to Wi-Fi or USB tethering first)") else out
    }
}
