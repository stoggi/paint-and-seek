package io.paintandseek.client.paint

import com.mojang.blaze3d.platform.NativeImage
import io.paintandseek.PaintAndSeek
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A static HSV colour wheel: hue around the circle, saturation along the radius,
 * drawn at full value. Built once into a [DynamicTexture] and blitted by the
 * paint screen; clicks map back to hue/sat via [sample].
 */
object ColorWheel {
    const val SIZE = 100
    private const val R = SIZE / 2f

    private var textureId: Identifier? = null

    /** Lazily build the wheel texture (render thread) and return its id. */
    fun textureId(): Identifier {
        textureId?.let { return it }
        val image = NativeImage(SIZE, SIZE, true)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val dx = x + 0.5f - R
                val dy = y + 0.5f - R
                val r = sqrt(dx * dx + dy * dy) / R
                if (r > 1f) {
                    image.setPixel(x, y, 0)
                } else {
                    val hue = (atan2(dy, dx) / (2.0 * Math.PI)).toFloat() + 0.5f
                    image.setPixel(x, y, ColorUtil.hsvToArgb(hue, r, 1f))
                }
            }
        }
        val texture = DynamicTexture({ "paintandseek/color_wheel" }, image)
        val id = PaintAndSeek.id("color_wheel")
        Minecraft.getInstance().textureManager.register(id, texture)
        texture.upload()
        textureId = id
        return id
    }

    /** Hue/sat at a cursor offset ([dx],[dy]) from the wheel's top-left, or null if outside. */
    fun sample(dx: Float, dy: Float): Pair<Float, Float>? {
        val cx = dx - R
        val cy = dy - R
        val r = sqrt(cx * cx + cy * cy) / R
        if (r > 1f) return null
        val hue = (atan2(cy, cx) / (2.0 * Math.PI)).toFloat() + 0.5f
        return ((hue % 1f + 1f) % 1f) to r.coerceIn(0f, 1f)
    }

    /** Screen position of the current hue/sat indicator, relative to the wheel top-left. */
    fun indicatorOffset(hue: Float, sat: Float): Pair<Float, Float> {
        val angle = (hue - 0.5f) * 2.0 * Math.PI
        return (R + cos(angle).toFloat() * sat * R) to (R + sin(angle).toFloat() * sat * R)
    }
}
