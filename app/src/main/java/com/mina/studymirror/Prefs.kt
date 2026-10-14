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
}
