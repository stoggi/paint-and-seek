package io.paintandseek.client.paint

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import io.paintandseek.PaintAndSeek
import io.paintandseek.skin.SkinImage
import net.minecraft.client.Minecraft

/**
 * Reads the local player's current skin into an ARGB pixel array so painting
 * starts from their real skin.
 *
 * Default/built-in skins are resource-backed and read directly; custom
 * (downloaded) skins aren't resources, so we read the live skin texture back
 * from the GPU. Both paths are delivered via [onReady]; the GPU path is async
 * (the callback runs on the render thread once the copy completes).
 */
object SkinSeed {
    private const val SIZE = SkinImage.WIDTH * SkinImage.HEIGHT

    fun seed(mc: Minecraft, onReady: (IntArray) -> Unit) {
        val player = mc.player ?: return onReady(greyBase())
        val id = player.skin.body().texturePath()

        readResource(mc, id)?.let { return onReady(it) }
        readFromGpu(mc, id, onReady)
    }

    private fun readResource(mc: Minecraft, id: net.minecraft.resources.Identifier): IntArray? {
        val resource = mc.resourceManager.getResource(id).orElse(null) ?: return null
        return try {
            resource.open().use { stream ->
                NativeImage.read(stream).use { image ->
                    if (image.width == SkinImage.WIDTH && image.height == SkinImage.HEIGHT) {
                        toArgb(image)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            PaintAndSeek.LOGGER.warn("SkinSeed: resource read of '{}' failed", id, e)
            null
        }
    }

    private fun readFromGpu(mc: Minecraft, id: net.minecraft.resources.Identifier, onReady: (IntArray) -> Unit) {
        val gpu = mc.textureManager.getTexture(id).texture
        if (gpu.getWidth(0) != SkinImage.WIDTH || gpu.getHeight(0) != SkinImage.HEIGHT) {
            PaintAndSeek.LOGGER.warn("SkinSeed: can't read skin texture '{}' from GPU — using grey base", id)
            return onReady(greyBase())
        }
        val width = SkinImage.WIDTH
        val height = SkinImage.HEIGHT
        val blockSize = gpu.format.blockSize()
        val device = RenderSystem.getDevice()
        val buffer = device.createBuffer({ "paintandseek skin readback" }, 9, width.toLong() * height * blockSize)
        device.createCommandEncoder().copyTextureToBuffer(gpu, buffer, 0L, {
            try {
                buffer.map(true, false).use { read ->
                    val image = NativeImage(width, height, false)
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            // Raw int is in the GPU's native ABGR order; preserve alpha.
                            image.setPixelABGR(x, y, read.data().getInt((x + y * width) * blockSize))
                        }
                    }
                    val pixels = toArgb(image)
                    image.close()
                    onReady(pixels)
                }
            } catch (e: Exception) {
                PaintAndSeek.LOGGER.warn("SkinSeed: GPU readback of '{}' failed — using grey base", id, e)
                onReady(greyBase())
            } finally {
                buffer.close()
            }
        }, 0)
    }

    private fun toArgb(image: NativeImage): IntArray {
        val pixels = IntArray(SIZE)
        var i = 0
        for (y in 0 until SkinImage.HEIGHT) {
            for (x in 0 until SkinImage.WIDTH) {
                pixels[i++] = image.getPixel(x, y)
            }
        }
        return pixels
    }

    private fun greyBase(): IntArray = IntArray(SIZE) { 0xFF808080.toInt() }
}
