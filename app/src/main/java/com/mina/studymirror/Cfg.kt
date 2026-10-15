package com.mina.studymirror

import android.content.Context
import android.graphics.Color

/** Look-and-feel and notification settings chosen on the Settings screen. */
object Cfg {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    val swatches: List<Pair<String, Int>> = listOf(
        "Sapphire" to Color.parseColor("#4F8CF6"),
        "Violet" to Color.parseColor("#9D7DFA"),
        "Emerald" to Color.parseColor("#3ECF8E"),
        "Amber" to Color.parseColor("#F59E0B"),
        "Rose" to Color.parseColor("#F43F5E"),
        "Teal" to Color.parseColor("#14B8A6")
    )

    val BREAK: Int = Color.parseColor("#3ECF8E")
    val VIOLET: Int = Color.parseColor("#9D7DFA")

    fun accent(c: Context): Int = sp(c).getInt("accent", swatches[0].second)
    fun setAccent(c: Context, v: Int) { sp(c).edit().putInt("accent", v).apply() }

    private fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    private fun put(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }

    // ---- which notifications to show
    fun status(c: Context) = bool(c, "n_status", true)
    fun setStatus(c: Context, v: Boolean) = put(c, "n_status", v)
    fun media(c: Context) = bool(c, "n_media", true)
    fun setMedia(c: Context, v: Boolean) = put(c, "n_media", v)
    fun live(c: Context) = bool(c, "n_live", true)
    fun setLive(c: Context, v: Boolean) = put(c, "n_live", v)
    fun alerts(c: Context) = bool(c, "n_alerts", true)
    fun setAlerts(c: Context, v: Boolean) = put(c, "n_alerts", v)
    fun island(c: Context) = bool(c, "n_island", true)
    fun setIsland(c: Context, v: Boolean) = put(c, "n_island", v)

    // ---- notch island look
    fun islandSize(c: Context): Int = sp(c).getInt("i_size", 1)            // 0 small, 1 medium, 2 large
    fun setIslandSize(c: Context, v: Int) { sp(c).edit().putInt("i_size", v).apply() }
    fun islandStyle(c: Context): Int = sp(c).getInt("i_style", 0)          // 0 black, 1 accent tint, 2 glass
    fun setIslandStyle(c: Context, v: Int) { sp(c).edit().putInt("i_style", v).apply() }
    fun islandNudge(c: Context): Int = sp(c).getInt("i_nudge", 0)          // extra dp from the top
    fun setIslandNudge(c: Context, v: Int) { sp(c).edit().putInt("i_nudge", v).apply() }
    fun islandPop(c: Context) = bool(c, "i_pop", true)
    fun setIslandPop(c: Context, v: Boolean) = put(c, "i_pop", v)

    // ---- full screens drawn over other apps
    fun blur(c: Context) = bool(c, "o_blur", true)
    fun setBlur(c: Context, v: Boolean) = put(c, "o_blur", v)
    fun useImages(c: Context) = bool(c, "o_images", true)
    fun setUseImages(c: Context, v: Boolean) = put(c, "o_images", v)
    fun dim(c: Context): Int = sp(c).getInt("o_dim", 60)                   // 20..90 percent
    fun setDim(c: Context, v: Int) { sp(c).edit().putInt("o_dim", v).apply() }
}
