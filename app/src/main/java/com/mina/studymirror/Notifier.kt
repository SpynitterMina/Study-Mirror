package com.mina.studymirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle

/** What the notifications need to know about the running session. */
class Snap(
    val mode: GuardService.Mode,
    val block: Int,
    val total: Int,
    val task: String,
    val leftMs: Long,        // -1 when the length of the block is not known
    val durMs: Long,
    val startWall: Long,
    val choice: String
)

/**
 * Four ways to show a session, each switchable in Settings:
 *  - a status notification with a ticking timer
 *  - a media-style card, which Android pins to the top of the shade and shows on the lock screen
 *  - a Live Update (Android 16): lock screen, top of the shade and a chip in the status bar
 *  - short alert banners at the moments that matter
 */
class Notifier(private val ctx: Context) {

    companion object {
        const val CH_STATUS = "session"
        const val CH_MEDIA = "media_card"
        const val CH_ALERT = "alerts"
        const val ID_STATUS = 1
        const val ID_MEDIA = 2
        const val ID_ALERT = 3
    }

    private val nm: NotificationManager = ctx.getSystemService(NotificationManager::class.java)
    private var session: MediaSession? = null

    fun init() {
        val status = NotificationChannel(CH_STATUS, "Study session", NotificationManager.IMPORTANCE_LOW)
        status.setShowBadge(false)
        status.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        val media = NotificationChannel(CH_MEDIA, "Session card (top of the shade)", NotificationManager.IMPORTANCE_LOW)
        media.setShowBadge(false)
        media.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        val alert = NotificationChannel(CH_ALERT, "Session alerts", NotificationManager.IMPORTANCE_HIGH)
        alert.enableVibration(true)
        alert.vibrationPattern = longArrayOf(0, 180, 80, 180)
        alert.setSound(null, null)
        alert.setShowBadge(false)
        alert.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        nm.createNotificationChannels(listOf(status, media, alert))
    }

    private fun label(n: Int, total: Int) = if (total > 0) "Block $n of $total" else "Block $n"

    private fun accentFor(s: Snap): Int = if (s.mode == GuardService.Mode.BREAK) Cfg.BREAK else Cfg.accent(ctx)

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    // ------------------------------------------------------------------ main entry

    fun update(s: Snap) {
        if (s.mode == GuardService.Mode.IDLE) {
            cancelSession()
            return
        }
        val title: String
        val text: String
        if (s.mode == GuardService.Mode.BREAK) {
            title = "Break" + if (s.choice.isNotEmpty()) " \u00B7 ${s.choice}" else ""
            text = "Back to ${label(s.block + 1, s.total)} when it ends"
        } else {
            title = "Focus \u00B7 ${label(s.block, s.total)}"
            text = if (s.task.isNotEmpty()) s.task else "You're doing very well"
        }
        try {
            if (Cfg.status(ctx)) postStatus(s, title, text) else nm.cancel(ID_STATUS)
        } catch (e: Exception) {}
        try {
            if (Cfg.media(ctx)) postMedia(s, title, text) else dropMedia()
        } catch (e: Exception) {}
    }

    fun cancelSession() {
        try { nm.cancel(ID_STATUS) } catch (e: Exception) {}
        dropMedia()
    }

    fun cancelAll() {
        cancelSession()
        try { nm.cancel(ID_ALERT) } catch (e: Exception) {}
    }

    // ------------------------------------------------------------- status + live update

    private fun postStatus(s: Snap, title: String, text: String) {
        val b = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(accentFor(s))

        val now = System.currentTimeMillis()
        if (s.leftMs >= 0) {
            b.setWhen(now + s.leftMs).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setWhen(s.startWall).setUsesChronometer(true).setChronometerCountDown(false)
        }

        // Android 16 Live Update: the system promotes an ongoing notification to the lock screen,
        // the top of the shade and a status bar chip. Older versions just ignore these extras.
        if (Build.VERSION.SDK_INT >= 36 && Cfg.live(ctx)) {
            val ex = Bundle()
            ex.putBoolean("android.requestPromotedOngoing", true)
            if (s.leftMs < 0) ex.putString("android.shortCriticalText", "Block ${s.block}")
            b.addExtras(ex)
        }
        nm.notify(ID_STATUS, b.build())
    }

    // ----------------------------------------------------------------- media card

    @Suppress("DEPRECATION")
    private fun postMedia(s: Snap, title: String, text: String) {
        val sess = session ?: MediaSession(ctx, "StudyMirror").also { session = it }
        val art = makeArt(s)

        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, text)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Study Mirror")
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
        if (s.durMs > 0) md.putLong(MediaMetadata.METADATA_KEY_DURATION, s.durMs)
        sess.setMetadata(md.build())

        val position = if (s.durMs > 0 && s.leftMs >= 0) (s.durMs - s.leftMs).coerceIn(0L, s.durMs) else PlaybackState.PLAYBACK_POSITION_UNKNOWN
        sess.setPlaybackState(
            PlaybackState.Builder()
                .setActions(0L)
                .setState(PlaybackState.STATE_PLAYING, position, 1f)
                .build()
        )
        sess.setSessionActivity(openApp())
        sess.isActive = true

        val n = Notification.Builder(ctx, CH_MEDIA)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(art)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(accentFor(s))
            .setStyle(Notification.MediaStyle().setMediaSession(sess.sessionToken))
            .build()
        nm.notify(ID_MEDIA, n)
    }

    private fun dropMedia() {
        try { nm.cancel(ID_MEDIA) } catch (e: Exception) {}
        val s = session
        if (s != null) {
            try {
                s.isActive = false
                s.release()
            } catch (e: Exception) {}
        }
        session = null
    }

    /** The square picture on the card: one of the user's images, or the app's glow with the block number. */
    private fun makeArt(s: Snap): Bitmap {
        val size = 512
        val accent = accentFor(s)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = GlowDrawable(accent, Backgrounds.random(ctx, 700), 150)
        bg.setBounds(0, 0, size, size)
        bg.draw(c)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 18f
        ring.strokeCap = Paint.Cap.ROUND
        ring.color = withAlpha(Color.WHITE, 40)
        val box = RectF(96f, 96f, 416f, 416f)
        c.drawArc(box, 0f, 360f, false, ring)
        ring.color = accent
        val frac = if (s.durMs > 0 && s.leftMs >= 0) (s.leftMs.toFloat() / s.durMs.toFloat()).coerceIn(0f, 1f) else 1f
        c.drawArc(box, -90f, 360f * frac, false, ring)

        val t = Paint(Paint.ANTI_ALIAS_FLAG)
        t.color = Color.WHITE
        t.textAlign = Paint.Align.CENTER
        t.typeface = Theme.mono(ctx, 700)
        t.textSize = 120f
        val big = if (s.mode == GuardService.Mode.BREAK) "Break" else if (s.total > 0) "${s.block}/${s.total}" else "${s.block}"
        if (big.length > 5) t.textSize = 86f
        c.drawText(big, size / 2f, size / 2f + 38f, t)
        t.typeface = Theme.sans(ctx, 500)
        t.textSize = 34f
        t.color = withAlpha(Color.WHITE, 190)
        c.drawText(if (s.mode == GuardService.Mode.BREAK) "recharge" else "focus", size / 2f, size / 2f + 92f, t)
        return bmp
    }

    // --------------------------------------------------------------------- alerts

    fun alert(title: String, text: String) {
        if (!Cfg.alerts(ctx)) return
        try {
            val n = Notification.Builder(ctx, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(9000L)
                .setColor(Cfg.accent(ctx))
                .setContentIntent(openApp())
                .build()
            nm.notify(ID_ALERT, n)
        } catch (e: Exception) {}
    }
}
