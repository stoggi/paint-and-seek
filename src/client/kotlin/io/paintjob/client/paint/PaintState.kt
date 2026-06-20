package io.paintjob.client.paint

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.PlayerModelType

/**
 * Client-side painting settings + transient pick info, shared between the paint
 * screen and HUD. (Colour wheel / eye-dropper will drive [colorArgb] later.)
 */
object PaintState {
    /** Current brush colour, packed ARGB. Defaults to opaque red. */
    var colorArgb: Int = 0xFFFF0000.toInt()

    /** Brush radius in texels (0 = single pixel, 1 = 3x3, ...). */
    var brushRadius: Int = 1

    /** Most recent pick, for the debug HUD. */
    var lastHit: TexelHit? = null

    fun modelType(mc: Minecraft): SkinModelType {
        val slim = mc.player?.skin?.model() == PlayerModelType.SLIM
        return if (slim) SkinModelType.SLIM else SkinModelType.WIDE
    }
}
