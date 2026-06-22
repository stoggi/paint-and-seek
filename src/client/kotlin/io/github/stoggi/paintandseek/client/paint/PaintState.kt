package io.github.stoggi.paintandseek.client.paint

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

    /** Brush diameter in texels (1, 2, 3, ...). */
    var brushSize: Int = 1

    /** Which skin layer is being painted/picked. */
    var layer: SkinLayer = SkinLayer.BASE
        private set

    /** Currently selected pose (drives picking; networked so others see it). */
    var pose: PaintPose = PaintPose.DEFAULT

    /** Restrict picking to one part group so obscured faces stay reachable. */
    var partFilter: PartFilter = PartFilter.ALL

    /** Transparent ("eraser") mode - only valid on the overlay layer. */
    var transparentMode: Boolean = false
        private set

    /** Most recent pick, for the debug HUD. */
    var lastHit: TexelHit? = null

    const val MAX_BRUSH_SIZE = 12

    fun toggleLayer() {
        layer = if (layer == SkinLayer.BASE) SkinLayer.OVERLAY else SkinLayer.BASE
        if (layer == SkinLayer.BASE) transparentMode = false // base can't be transparent
    }

    /** Select transparent painting (only the overlay may be cleared). */
    fun selectTransparent() {
        if (layer == SkinLayer.OVERLAY) transparentMode = true
    }

    /** Go back to painting the picked colour. */
    fun selectColor() {
        transparentMode = false
    }

    /** The colour a stroke writes: transparent only when allowed, else the picked colour. */
    fun effectivePaintColor(): Int =
        if (transparentMode && layer == SkinLayer.OVERLAY) 0 else colorArgb

    fun setHueSat(h: Float, s: Float) {
        hue = h.coerceIn(0f, 1f)
        sat = s.coerceIn(0f, 1f)
        transparentMode = false
        recompute()
    }

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        transparentMode = false
        recompute()
    }

    /** Set the colour from a sampled ARGB (eye-dropper), syncing the HSV wheel. */
    fun setFromArgb(argb: Int) {
        val hsv = ColorUtil.argbToHsv(argb)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        transparentMode = false
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
