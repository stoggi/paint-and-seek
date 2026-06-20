package io.paintjob.client.paint

import com.mojang.blaze3d.platform.NativeImage
import io.paintjob.Paintjob
import io.paintjob.skin.SkinImage
import net.minecraft.client.Minecraft

/**
 * Reads the local player's current skin into an ARGB pixel array, so painting
 * starts from their real skin instead of a blank (otherwise the body would turn
 * invisible the moment the painted texture takes over).
 *
 * Works for resource-backed skins (the default Steve/Alex, and the dev player).
 * Real downloaded skins aren't readable this way yet — those fall back to an
 * opaque grey base. (Reading downloaded skins back from the GPU is a later step.)
 */
object SkinSeed {
    private const val SIZE = SkinImage.WIDTH * SkinImage.HEIGHT

    fun readCurrentSkin(mc: Minecraft): IntArray {
        val player = mc.player
        if (player != null) {
            val id = player.skin.body().texturePath()
            val resource = mc.resourceManager.getResource(id).orElse(null)
            if (resource != null) {
                try {
                    resource.open().use { stream ->
                        NativeImage.read(stream).use { image ->
                            if (image.width == SkinImage.WIDTH && image.height == SkinImage.HEIGHT) {
                                val pixels = IntArray(SIZE)
                                var i = 0
                                for (y in 0 until SkinImage.HEIGHT) {
                                    for (x in 0 until SkinImage.WIDTH) {
                                        pixels[i++] = image.getPixel(x, y)
                                    }
                                }
                                return pixels
                            }
                        }
                    }
                } catch (e: Exception) {
                    Paintjob.LOGGER.warn("Couldn't read current skin {} to seed painting", id, e)
                }
            }
        }
        return greyBase()
    }

    private fun greyBase(): IntArray = IntArray(SIZE) { 0xFF808080.toInt() }
}
