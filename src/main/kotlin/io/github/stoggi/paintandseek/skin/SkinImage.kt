package io.github.stoggi.paintandseek.skin

/**
 * A server-safe representation of a painted player skin.
 *
 * Deliberately free of any `net.minecraft.client` imports so it can live on the
 * logical server (and in shared code). Pixels are stored as packed ARGB ints in
 * row-major order, matching the standard 64x64 Minecraft skin layout.
 *
 * The client converts to/from `NativeImage`; the server only needs to store,
 * patch, and rebroadcast.
 */
class SkinImage private constructor(
    val pixels: IntArray,
) {
    init {
        require(pixels.size == WIDTH * HEIGHT) { "Skin must be ${WIDTH}x$HEIGHT, got ${pixels.size} pixels" }
    }

    /** Apply a rectangular [patch] of pixels with its top-left at ([x], [y]). */
    fun applyPatch(x: Int, y: Int, w: Int, h: Int, patch: IntArray) {
        require(patch.size == w * h) { "Patch size ${patch.size} != ${w}x$h" }
        require(x >= 0 && y >= 0 && x + w <= WIDTH && y + h <= HEIGHT) {
            "Patch rect ($x,$y,$w,$h) out of bounds"
        }
        for (row in 0 until h) {
            val srcStart = row * w
            val dstStart = (y + row) * WIDTH + x
            patch.copyInto(pixels, dstStart, srcStart, srcStart + w)
        }
    }

    /** Extract the rectangular region at ([x], [y]) of size [w]x[h] as a fresh array. */
    fun region(x: Int, y: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (row in 0 until h) {
            val srcStart = (y + row) * WIDTH + x
            pixels.copyInto(out, row * w, srcStart, srcStart + w)
        }
        return out
    }

    fun copy(): SkinImage = SkinImage(pixels.copyOf())

    companion object {
        const val WIDTH = 64
        const val HEIGHT = 64

        fun blank(): SkinImage = SkinImage(IntArray(WIDTH * HEIGHT))

        fun of(pixels: IntArray): SkinImage = SkinImage(pixels.copyOf())

        /** Pack an ARGB int array into a big-endian byte array for the wire. */
        fun toBytes(pixels: IntArray): ByteArray {
            val out = ByteArray(pixels.size * 4)
            for (i in pixels.indices) {
                val p = pixels[i]
                val o = i * 4
                out[o] = (p ushr 24).toByte()
                out[o + 1] = (p ushr 16).toByte()
                out[o + 2] = (p ushr 8).toByte()
                out[o + 3] = p.toByte()
            }
            return out
        }

        /** Inverse of [toBytes]. */
        fun fromBytes(bytes: ByteArray): IntArray {
            require(bytes.size % 4 == 0) { "Byte array length ${bytes.size} not a multiple of 4" }
            val out = IntArray(bytes.size / 4)
            for (i in out.indices) {
                val o = i * 4
                out[i] = ((bytes[o].toInt() and 0xFF) shl 24) or
                    ((bytes[o + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[o + 2].toInt() and 0xFF) shl 8) or
                    (bytes[o + 3].toInt() and 0xFF)
            }
            return out
        }
    }
}
