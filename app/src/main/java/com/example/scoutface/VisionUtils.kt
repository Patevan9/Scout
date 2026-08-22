package com.example.scoutface

import android.graphics.Rect
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

object VisionUtils {

    fun keepVisionLabel(label: String): Boolean {
        val bad = setOf(
            "beard", "facial hair", "moustache", "mustache", "jaw", "forehead", "chin",
            "eyebrow", "eyelash", "nose", "lip", "neck", "sleeve",
            "goggles", "smile"
        )
        return label.isNotBlank() && !bad.contains(label)
    }

    fun faceFingerprintFromBoxStable(box: Rect, imgW: Int, imgH: Int): String {
        fun quantize01(v: Float, step: Float): Float {
            val clamped = v.coerceIn(0f, 1f)
            return (clamped / step).roundToInt() * step
        }

        val cx = quantize01(box.centerX().toFloat() / imgW, 0.03f)
        val cy = quantize01(box.centerY().toFloat() / imgH, 0.03f)
        val bw = quantize01(box.width().toFloat() / imgW, 0.04f)
        val bh = quantize01(box.height().toFloat() / imgH, 0.04f)

        val buf = ByteBuffer.allocate(16)
        buf.putFloat(cx)
        buf.putFloat(cy)
        buf.putFloat(bw)
        buf.putFloat(bh)

        return hashBytes(buf.array())
    }

    private fun hashBytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}