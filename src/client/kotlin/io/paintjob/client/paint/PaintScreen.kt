package io.paintjob.client.paint

import io.paintjob.client.skin.PaintedSkinTextures
import io.paintjob.net.SkinRect
import io.paintjob.net.SubmitSkinPatch
import io.paintjob.skin.SkinImage
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component

/**
 * The painting overlay. Does NOT pause the game and draws no background, so the
 * world + the player (front third-person) stay crisp behind it.
 *
 * Controls:
 *  - Left-click / drag on the model: paint the picked texel.
 *  - Right-click anywhere: eye-dropper — sample the on-screen pixel as the colour.
 *  - Colour wheel (hue/sat) + value slider (bottom-left): pick a custom colour.
 */
class PaintScreen : Screen(Component.literal("Paintjob")) {

    private val wheelX get() = 12
    private val wheelY get() = height - 12 - ColorWheel.SIZE
    private val sliderX get() = wheelX + ColorWheel.SIZE + 10
    private val sliderY get() = wheelY
    private val sliderW = 14
    private val sliderH get() = ColorWheel.SIZE
    private val swatchX get() = wheelX
    private val swatchY get() = wheelY - 28
    private val swatchSize = 24

    override fun isPauseScreen(): Boolean = false
    override fun isInGameUi(): Boolean = true
    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) { /* keep world crisp */ }

    override fun removed() {
        PaintMode.restoreCamera()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (handlePointer(event.x(), event.y(), event.button())) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (handlePointer(event.x(), event.y(), event.button())) return true
        return super.mouseDragged(event, dragX, dragY)
    }

    /** Route a click/drag to the colour UI, the eye-dropper, or painting. */
    private fun handlePointer(x: Double, y: Double, button: Int): Boolean {
        when (button) {
            0 -> {
                if (inWheel(x, y)) {
                    ColorWheel.sample((x - wheelX).toFloat(), (y - wheelY).toFloat())?.let {
                        PaintState.setHueSat(it.first, it.second)
                    }
                    return true
                }
                if (inSlider(x, y)) {
                    PaintState.setValue(1f - ((y - sliderY) / sliderH).toFloat())
                    return true
                }
                paintAt(x, y)
                return true
            }
            1 -> {
                eyedropAt(x, y)
                return true
            }
        }
        return false
    }

    private fun inWheel(x: Double, y: Double) =
        x >= wheelX && x < wheelX + ColorWheel.SIZE && y >= wheelY && y < wheelY + ColorWheel.SIZE

    private fun inSlider(x: Double, y: Double) =
        x >= sliderX && x < sliderX + sliderW && y >= sliderY && y < sliderY + sliderH

    private fun paintAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val player = mc.player ?: return
        val hit = PaintRaycaster.pick(mc, mouseX, mouseY, width, height, PaintState.modelType(mc))
        PaintState.lastHit = hit
        if (hit == null) return

        val r = PaintState.brushRadius
        val x0 = (hit.texelX - r).coerceIn(0, SkinImage.WIDTH - 1)
        val y0 = (hit.texelY - r).coerceIn(0, SkinImage.HEIGHT - 1)
        val x1 = (hit.texelX + r).coerceIn(0, SkinImage.WIDTH - 1)
        val y1 = (hit.texelY + r).coerceIn(0, SkinImage.HEIGHT - 1)
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val pixels = IntArray(w * h) { PaintState.colorArgb }
        val rect = SkinRect(x0, y0, w, h, pixels)

        PaintedSkinTextures.applyPatch(player.uuid, rect)
        ClientPlayNetworking.send(SubmitSkinPatch(rect))
    }

    /** Sample the rendered pixel under the cursor and adopt it as the colour. */
    private fun eyedropAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val target = mc.gameRenderer.mainRenderTarget()
        val sw = width.toDouble()
        val sh = height.toDouble()
        Screenshot.takeScreenshot(target) { image ->
            val fx = (mouseX / sw * image.width).toInt().coerceIn(0, image.width - 1)
            val fy = (mouseY / sh * image.height).toInt().coerceIn(0, image.height - 1)
            PaintState.setFromArgb(image.getPixel(fx, fy) or (0xFF shl 24))
            image.close()
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = minecraft

        // Live pick under the cursor for the debug readout (no painting).
        if (!inWheel(mouseX.toDouble(), mouseY.toDouble()) && !inSlider(mouseX.toDouble(), mouseY.toDouble())) {
            PaintState.lastHit = PaintRaycaster.pick(mc, mouseX.toDouble(), mouseY.toDouble(), width, height, PaintState.modelType(mc))
        }

        // Reticle with a centre gap, so eye-dropping samples the world, not the reticle.
        val white = 0xFFFFFFFF.toInt()
        graphics.fill(mouseX - 8, mouseY, mouseX - 2, mouseY + 1, white)
        graphics.fill(mouseX + 3, mouseY, mouseX + 9, mouseY + 1, white)
        graphics.fill(mouseX, mouseY - 8, mouseX + 1, mouseY - 2, white)
        graphics.fill(mouseX, mouseY + 3, mouseX + 1, mouseY + 9, white)

        renderColorPicker(graphics)

        // Debug + help text.
        val hit = PaintState.lastHit
        val text = if (hit != null) "${hit.part} texel (${hit.texelX}, ${hit.texelY})" else "no hit — aim at your character"
        graphics.text(this.font, text, swatchX + swatchSize + 8, swatchY + 2, white, true)
        graphics.text(this.font, "L-click paint · R-click eye-dropper · Esc exit", swatchX + swatchSize + 8, swatchY + 14, 0xFFBBBBBB.toInt(), true)
    }

    private fun renderColorPicker(graphics: GuiGraphicsExtractor) {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        // Current colour swatch.
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchSize + 1, swatchY + swatchSize + 1, black)
        graphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, PaintState.colorArgb)

        // Hue/sat wheel.
        graphics.blit(
            RenderPipelines.GUI_TEXTURED, ColorWheel.textureId(),
            wheelX, wheelY, 0f, 0f, ColorWheel.SIZE, ColorWheel.SIZE, ColorWheel.SIZE, ColorWheel.SIZE,
        )
        val (ix, iy) = ColorWheel.indicatorOffset(PaintState.hue, PaintState.sat)
        val cx = wheelX + ix.toInt()
        val cy = wheelY + iy.toInt()
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, black)
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, white)

        // Value slider (full-value colour -> black), with a marker.
        graphics.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, black)
        graphics.fillGradient(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, ColorUtil.hsvToArgb(PaintState.hue, PaintState.sat, 1f), black)
        val my = sliderY + ((1f - PaintState.value) * sliderH).toInt()
        graphics.fill(sliderX - 2, my - 1, sliderX + sliderW + 2, my + 1, white)
    }
}
