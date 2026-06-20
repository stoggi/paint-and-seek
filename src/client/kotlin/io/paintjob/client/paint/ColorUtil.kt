package io.paintjob.client.paint

/** Small HSV<->ARGB helpers (all components 0..1, alpha forced opaque). */
object ColorUtil {
    /** HSV (h,s,v in 0..1) -> packed opaque ARGB. */
    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 1f) + 1f) % 1f * 6f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return (0xFF shl 24) or
            ((r * 255f + 0.5f).toInt() shl 16) or
            ((g * 255f + 0.5f).toInt() shl 8) or
            (b * 255f + 0.5f).toInt()
    }

    /** Packed ARGB -> [hue, sat, value], each 0..1. */
    fun argbToHsv(argb: Int): FloatArray {
        val r = (argb ushr 16 and 0xFF) / 255f
        val g = (argb ushr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val v = max
        val s = if (max <= 0f) 0f else delta / max
        var h = when {
            delta <= 0f -> 0f
            max == r -> ((g - b) / delta) % 6f
            max == g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } / 6f
        if (h < 0f) h += 1f
        return floatArrayOf(h, s, v)
    }
}
