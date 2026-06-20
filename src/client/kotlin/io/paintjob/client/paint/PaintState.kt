package io.paintjob.client.paint

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.PlayerModelType

/**
 * Client-side painting settings + transient pick info, shared between the paint
 * screen and HUD. The current colour is stored as HSV (the colour wheel's source
 * of truth) and cached as packed ARGB for painting.
 */
object PaintState {
    var hue: Float = 0f      // 0..1
        private set
    var sat: Float = 1f      // 0..1
        private set
    var value: Float = 1f    // 0..1
        private set

    /** Current brush colour, packed ARGB (derived from HSV). */
    var colorArgb: Int = ColorUtil.hsvToArgb(0f, 1f, 1f)
        private set

    /** Brush radius in texels (0 = single pixel, 1 = 3x3, ...). */
    var brushRadius: Int = 1

    /** Most recent pick, for the debug HUD. */
    var lastHit: TexelHit? = null

    fun setHueSat(h: Float, s: Float) {
        hue = h.coerceIn(0f, 1f)
        sat = s.coerceIn(0f, 1f)
        recompute()
    }

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        recompute()
    }

    /** Set the colour from a sampled ARGB (eye-dropper), syncing the HSV wheel. */
    fun setFromArgb(argb: Int) {
        val hsv = ColorUtil.argbToHsv(argb)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        recompute()
    }

    private fun recompute() {
        colorArgb = ColorUtil.hsvToArgb(hue, sat, value)
    }

    fun modelType(mc: Minecraft): SkinModelType {
        val slim = mc.player?.skin?.model() == PlayerModelType.SLIM
        return if (slim) SkinModelType.SLIM else SkinModelType.WIDE
    }
}
