package com.mina.studymirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** The big circular timer on the main screen. Draws a ring, the time, and one label line. */
class RingView(c: Context) : View(c) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#243847") }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAF2EE"); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9FB4C4"); textAlign = Paint.Align.CENTER }
    private var target = 0f
    private var shown = 0f
    private var bigText = ""
    private var label = ""

    fun set(progress: Float, bigT: String, labelT: String, color: Int) {
        target = progress.coerceIn(0f, 1f)
        bigText = bigT
        label = labelT
        bar.color = color
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val s = w * 0.045f
        track.strokeWidth = s
        bar.strokeWidth = s
        val r = RectF(s, s, w - s, w - s)
        cv.drawArc(r, 0f, 360f, false, track)
        shown += (target - shown) * 0.2f
        cv.drawArc(r, -90f, 360f * shown, false, bar)
        big.textSize = w * 0.19f
        small.textSize = w * 0.058f
        cv.drawText(bigText, w / 2, w / 2 + w * 0.05f, big)
        cv.drawText(label, w / 2, w / 2 + w * 0.17f, small)
        if (Math.abs(target - shown) > 0.002f) postInvalidateOnAnimation()
    }
}
