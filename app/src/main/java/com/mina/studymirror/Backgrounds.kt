package com.mina.studymirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** The user's own pictures, copied into the app so they stay available. */
object Backgrounds {
    const val MAX = 12

    private fun dir(c: Context): File {
        val d = File(c.filesDir, "bg")
        d.mkdirs()
        return d
    }

    fun list(c: Context): List<File> =
        (dir(c).listFiles { f -> f.name.endsWith(".jpg") }?.toList() ?: emptyList()).sortedBy { it.name }

    fun remove(c: Context, f: File) {
        try { f.delete() } catch (e: Exception) {}
    }

    fun add(c: Context, uri: Uri): Boolean {
        if (list(c).size >= MAX) return false
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longSide = max(bounds.outWidth, bounds.outHeight)
            while (longSide / sample > 2200) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

            val rotation = try {
                c.contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) { 0f }
            if (rotation != 0f) {
                val m = Matrix()
                m.postRotate(rotation)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            val out = File(dir(c), "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun decode(f: File, maxSide: Int): Bitmap? {
        return try {
            val b = BitmapFactory.Options()
            b.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, b)
            var sample = 1
            while (max(b.outWidth, b.outHeight) / sample > maxSide) sample *= 2
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            BitmapFactory.decodeFile(f.absolutePath, o)
        } catch (e: Exception) {
            null
        }
    }

    /** A random picture for a full screen, or null when none were added or the option is off. */
    fun random(c: Context, maxSide: Int = 1600): Bitmap? {
        if (!Cfg.useImages(c)) return null
        val files = list(c)
        if (files.isEmpty()) return null
        return decode(files.random(), maxSide)
    }
}
