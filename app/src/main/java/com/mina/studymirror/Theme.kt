package com.mina.studymirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import kotlin.math.max

/** Colors and fonts shared with the laptop page: dark base, glass surfaces, one accent. */
object Theme {
    val INK: Int = Color.parseColor("#F0F6F4")
    val SOFT: Int = Color.parseColor("#9CB1C2")
    val MUTED: Int = Color.parseColor("#5E7688")
    val DARK_ON_ACCENT: Int = Color.parseColor("#07121A")

    private val cache = HashMap<String, Typeface>()

    private fun load(c: Context, asset: String, weight: Int, fallback: Typeface): Typeface {
        val key = "$asset$weight"
        cache[key]?.let { return it }
        var t: Typeface? = null
        try {
            t = Typeface.Builder(c.applicationContext.assets, asset)
                .setFontVariationSettings("'wght' $weight")
                .build()
        } catch (e: Exception) {}
        val out = t ?: fallback
        cache[key] = out
        return out
    }

    /** Plus Jakarta Sans, the laptop page's text font. */
    fun sans(c: Context, weight: Int = 500): Typeface =
        load(c, "jakarta.ttf", weight, Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL))

    /** JetBrains Mono, used for the timer digits. */
    fun mono(c: Context, weight: Int = 700): Typeface =
        load(c, "mono.ttf", weight, Typeface.MONOSPACE)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun withAlpha(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

/** A translucent "glass" panel: faint white fill, soft sheen, hairline border. */
fun Context.glass(radiusDp: Int = 20, strong: Boolean = false): GradientDrawable {
    val top = if (strong) 0x30FFFFFF else 0x22FFFFFF
    val bottom = if (strong) 0x16FFFFFF else 0x0DFFFFFF
    val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom))
    g.cornerRadius = dp(radiusDp).toFloat()
    g.setStroke(dp(1), 0x2EFFFFFF)
    return g
}

fun Context.fill(color: Int, radiusDp: Int): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = dp(radiusDp).toFloat()
    return g
}

/**
 * The app background: dark base, two soft glows in the accent color, and optionally one of the
 * user's pictures underneath a dark veil so text stays readable.
 */
class GlowDrawable(
    private val accent: Int,
    private val image: Bitmap? = null,
    private val veil: Int = 150,
    private val baseAlpha: Int = 255
) : Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        p.shader = null
        val img = image
        if (img != null) {
            val s = max(w / img.width, h / img.height)
            val dw = img.width * s
            val dh = img.height * s
            val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            p.color = Color.WHITE
            c.drawBitmap(img, null, dst, p)
            p.color = Color.argb(veil, 8, 12, 16)
            c.drawRect(0f, 0f, w, h, p)
        } else {
            p.color = Color.argb(baseAlpha, 8, 12, 16)
            c.drawRect(0f, 0f, w, h, p)
        }
        val big = max(w, h)
        p.shader = RadialGradient(w * 0.12f, h * 0.06f, big * 0.75f, withAlpha(accent, 86), withAlpha(accent, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.95f, h * 0.97f, big * 0.7f, withAlpha(accent, 48), withAlpha(accent, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Tiny press feedback: the view shrinks a little while a finger is on it. */
fun android.view.View.press() {
    setOnTouchListener { v, e ->
        when (e.action) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

/** Fade and slide a view in. */
fun android.view.View.rise(delayMs: Long, distancePx: Float) {
    alpha = 0f
    translationY = distancePx
    animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(450)
        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
}
