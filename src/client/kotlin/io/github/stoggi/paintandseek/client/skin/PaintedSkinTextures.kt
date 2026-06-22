package io.github.stoggi.paintandseek.client.skin

import com.mojang.blaze3d.platform.NativeImage
import io.github.stoggi.paintandseek.PaintAndSeek
import io.github.stoggi.paintandseek.net.SkinRect
import io.github.stoggi.paintandseek.skin.SkinImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side registry of painted skins.
 *
 * For each painted player we hold a [NativeImage] backing a GPU [DynamicTexture]
 * registered under a stable [Identifier]. The `AbstractClientPlayer.getSkin`
 * mixin swaps the body texture to that id, and [AvatarRenderer] re-reads it every
 * frame - so uploads here are reflected live on the model.
 *
 * All access happens on the client thread (network receivers are scheduled there).
 */
object PaintedSkinTextures {
    private class Entry(val image: NativeImage, val texture: DynamicTexture, val textureId: Identifier)

    private val entries = ConcurrentHashMap<UUID, Entry>()

    /** The texture id the mixin should render for [uuid], or null for vanilla skin. */
    fun textureId(uuid: UUID): Identifier? = entries[uuid]?.textureId

    fun has(uuid: UUID): Boolean = entries.containsKey(uuid)

    /** Current ARGB value of a texel for [uuid] (0 if none). */
    fun pixel(uuid: UUID, x: Int, y: Int): Int =
        entries[uuid]?.image?.getPixel(x, y) ?: 0

    /** Replace the whole skin for [uuid] with [pixels] (ARGB, 64x64 row-major). */
    fun applySnapshot(uuid: UUID, pixels: IntArray) {
        // Drop malformed snapshots rather than crash on a hostile/buggy server.
        if (pixels.size != SkinImage.WIDTH * SkinImage.HEIGHT) return
        val entry = entries.getOrPut(uuid) { createEntry(uuid) }
        var i = 0
        for (y in 0 until SkinImage.HEIGHT) {
            for (x in 0 until SkinImage.WIDTH) {
                entry.image.setPixel(x, y, pixels[i++])
            }
        }
        entry.texture.upload()
    }

    /** Apply an incremental stroke [rect] for [uuid] and re-upload. */
    fun applyPatch(uuid: UUID, rect: SkinRect) {
        // Reject out-of-bounds rects so a hostile server can't write past the image.
        if (!rect.fitsSkin()) return
        val entry = entries.getOrPut(uuid) { createEntry(uuid) }
        var i = 0
        for (dy in 0 until rect.h) {
            for (dx in 0 until rect.w) {
                entry.image.setPixel(rect.x + dx, rect.y + dy, rect.pixels[i++])
            }
        }
        entry.texture.upload()
    }

    /** Drop the painted skin for [uuid], reverting the model to its vanilla skin. */
    fun clear(uuid: UUID) {
        entries.remove(uuid)?.texture?.close()
    }

    fun clearAll() {
        entries.values.forEach { it.texture.close() }
        entries.clear()
    }

    private fun createEntry(uuid: UUID): Entry {
        val image = NativeImage(SkinImage.WIDTH, SkinImage.HEIGHT, true)
        val texture = DynamicTexture({ "paintandseek/$uuid" }, image)
        val id = PaintAndSeek.id("painted/$uuid")
        Minecraft.getInstance().textureManager.register(id, texture)
        return Entry(image, texture, id)
    }
}
