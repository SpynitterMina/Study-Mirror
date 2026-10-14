package com.mina.studymirror

import android.content.Context

object Prefs {
    val defaultEscape = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android"
    )

    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun escape(c: Context): Set<String> {
        val saved = sp(c).getStringSet("escape", null)
        return if (saved == null) defaultEscape else HashSet(saved)
    }

    fun setEscape(c: Context, s: Set<String>) {
        sp(c).edit().putStringSet("escape", HashSet(s)).apply()
    }

    fun token(c: Context): String {
        val p = sp(c)
        val existing = p.getString("token", null)
        if (existing != null) return existing
        val fresh = (100000..999999).random().toString()
        p.edit().putString("token", fresh).apply()
        return fresh
    }

    // ---- today's stats (keys carry the date, so they reset by themselves)
    private fun day() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    fun bump(c: Context, k: String) {
        val key = day() + "_" + k
        val p = sp(c)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun count(c: Context, k: String): Int = sp(c).getInt(day() + "_" + k, 0)

    // ---- rules: block-list or allow-only
    fun allowOnly(c: Context) = sp(c).getBoolean("allowOnly", false)
    fun setAllowOnly(c: Context, v: Boolean) { sp(c).edit().putBoolean("allowOnly", v).apply() }
    fun allowed(c: Context): Set<String> = HashSet(sp(c).getStringSet("allowed", emptySet()) ?: emptySet())
    fun setAllowed(c: Context, s: Set<String>) { sp(c).edit().putStringSet("allowed", HashSet(s)).apply() }

    // ---- remembered break choice and the laptop we paired with
    fun breakChoice(c: Context): String = sp(c).getString("choice", "") ?: ""
    fun setBreakChoice(c: Context, v: String) { sp(c).edit().putString("choice", v).apply() }
    fun laptop(c: Context): String = sp(c).getString("laptop", "") ?: ""
    fun setLaptop(c: Context, v: String) { sp(c).edit().putString("laptop", v).apply() }

    // ---- parked notes (newest first)
    fun notes(c: Context): List<String> =
        (sp(c).getString("notes", "") ?: "").split("\u0001").filter { it.isNotBlank() }

    fun addNote(c: Context, text: String) {
        val l = notes(c).toMutableList()
        l.add(0, text)
        sp(c).edit().putString("notes", l.take(30).joinToString("\u0001")).apply()
    }

    fun clearNotes(c: Context) {
        sp(c).edit().remove("notes").apply()
    }
}
