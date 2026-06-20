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
import kotlin.math.roundToInt

/**
 * The painting overlay. Does NOT pause the game and draws no background, so the
 * world + the player (orbit camera) stay crisp behind it.
 *
 * Controls:
 *  - Left-click / drag on the model: paint the picked texel.
 *  - Right-click anywhere: eye-dropper — sample the on-screen pixel as the colour.
 *  - Colour wheel (hue/sat) + value slider: pick a custom colour.
 *  - Layer button: toggle painting the base skin vs the overlay (2nd) layer.
 *  - Brush slider: brush size. WASD/arrows orbit, scroll zooms.
 */
class PaintScreen : Screen(Component.literal("Paintjob")) {

    private val wheelX get() = 12
    private val wheelY get() = height - 12 - ColorWheel.SIZE
    private val valueSliderX get() = wheelX + ColorWheel.SIZE + 10
    private val valueSliderY get() = wheelY
    private val valueSliderW = 14
    private val valueSliderH get() = ColorWheel.SIZE
    private val swatchX get() = wheelX
    private val swatchY get() = wheelY - 28
    private val swatchSize = 24

    private val controlsX get() = valueSliderX + valueSliderW + 14
    private val toggleX get() = controlsX
    private val toggleY get() = wheelY
    private val toggleW = 130
    private val toggleH = 18
    private val brushSliderX get() = controlsX
    private val brushSliderY get() = wheelY + 36
    private val brushSliderW = 130
    private val brushSliderH = 10

    override fun isPauseScreen(): Boolean = false
    override fun isInGameUi(): Boolean = true
    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) { /* keep world crisp */ }

    override fun removed() {
        PaintMode.restoreCamera()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (handlePointer(event.x(), event.y(), event.button(), isClick = true)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (handlePointer(event.x(), event.y(), event.button(), isClick = false)) return true
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        PaintCamera.zoom(scrollY)
        return true
    }

    /** Route a click/drag to the colour UI, layer/brush controls, eye-dropper, or painting. */
    private fun handlePointer(x: Double, y: Double, button: Int, isClick: Boolean): Boolean {
        when (button) {
            0 -> {
                if (inRect(x, y, wheelX, wheelY, ColorWheel.SIZE, ColorWheel.SIZE)) {
                    ColorWheel.sample((x - wheelX).toFloat(), (y - wheelY).toFloat())?.let {
                        PaintState.setHueSat(it.first, it.second)
                    }
                    return true
                }
                if (inRect(x, y, valueSliderX, valueSliderY, valueSliderW, valueSliderH)) {
                    PaintState.setValue(1f - ((y - valueSliderY) / valueSliderH).toFloat())
                    return true
                }
                if (inRect(x, y, brushSliderX, brushSliderY, brushSliderW, brushSliderH)) {
                    val frac = ((x - brushSliderX) / brushSliderW).coerceIn(0.0, 1.0)
                    PaintState.brushRadius = (frac * PaintState.MAX_BRUSH_RADIUS).roundToInt()
                    return true
                }
                if (inRect(x, y, toggleX, toggleY, toggleW, toggleH)) {
                    if (isClick) {
                        PaintState.layer =
                            if (PaintState.layer == SkinLayer.BASE) SkinLayer.OVERLAY else SkinLayer.BASE
                    }
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

    private fun inRect(x: Double, y: Double, rx: Int, ry: Int, rw: Int, rh: Int) =
        x >= rx && x < rx + rw && y >= ry && y < ry + rh

    private fun overUi(x: Double, y: Double): Boolean =
        inRect(x, y, wheelX, wheelY, ColorWheel.SIZE, ColorWheel.SIZE) ||
            inRect(x, y, valueSliderX, valueSliderY, valueSliderW, valueSliderH) ||
            inRect(x, y, brushSliderX, brushSliderY, brushSliderW, brushSliderH) ||
            inRect(x, y, toggleX, toggleY, toggleW, toggleH)

    private fun paintAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val player = mc.player ?: return
        val hit = PaintRaycaster.pick(mc, mouseX, mouseY, width, height, PaintState.modelType(mc), PaintState.layer)
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

        // Orbit the camera from held WASD / arrow keys.
        PaintCamera.pollKeys(mc)

        // Live pick under the cursor for the debug readout (no painting).
        if (!overUi(mouseX.toDouble(), mouseY.toDouble())) {
            PaintState.lastHit = PaintRaycaster.pick(mc, mouseX.toDouble(), mouseY.toDouble(), width, height, PaintState.modelType(mc), PaintState.layer)
        }

        // Reticle with a centre gap, so eye-dropping samples the world, not the reticle.
        val white = 0xFFFFFFFF.toInt()
        graphics.fill(mouseX - 8, mouseY, mouseX - 2, mouseY + 1, white)
        graphics.fill(mouseX + 3, mouseY, mouseX + 9, mouseY + 1, white)
        graphics.fill(mouseX, mouseY - 8, mouseX + 1, mouseY - 2, white)
        graphics.fill(mouseX, mouseY + 3, mouseX + 1, mouseY + 9, white)

        renderColorPicker(graphics)
        renderControls(graphics)
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
        graphics.fill(valueSliderX - 1, valueSliderY - 1, valueSliderX + valueSliderW + 1, valueSliderY + valueSliderH + 1, black)
        graphics.fillGradient(valueSliderX, valueSliderY, valueSliderX + valueSliderW, valueSliderY + valueSliderH, ColorUtil.hsvToArgb(PaintState.hue, PaintState.sat, 1f), black)
        val my = valueSliderY + ((1f - PaintState.value) * valueSliderH).toInt()
        graphics.fill(valueSliderX - 2, my - 1, valueSliderX + valueSliderW + 2, my + 1, white)
    }

    private fun renderControls(graphics: GuiGraphicsExtractor) {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val grey = 0xFF555555.toInt()
        val accent = 0xFF6DA8FF.toInt()

        // Layer toggle button.
        graphics.fill(toggleX - 1, toggleY - 1, toggleX + toggleW + 1, toggleY + toggleH + 1, black)
        graphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, grey)
        val layerName = if (PaintState.layer == SkinLayer.OVERLAY) "Overlay" else "Base"
        graphics.text(this.font, "Layer: $layerName", toggleX + 6, toggleY + 5, white, true)

        // Brush size slider.
        val diameter = PaintState.brushRadius * 2 + 1
        graphics.text(this.font, "Brush: ${diameter}px", brushSliderX, brushSliderY - 11, white, true)
        graphics.fill(brushSliderX - 1, brushSliderY - 1, brushSliderX + brushSliderW + 1, brushSliderY + brushSliderH + 1, black)
        graphics.fill(brushSliderX, brushSliderY, brushSliderX + brushSliderW, brushSliderY + brushSliderH, grey)
        val frac = PaintState.brushRadius.toFloat() / PaintState.MAX_BRUSH_RADIUS
        val knobX = brushSliderX + (frac * brushSliderW).toInt()
        graphics.fill(knobX - 2, brushSliderY - 2, knobX + 2, brushSliderY + brushSliderH + 2, accent)

        // Debug + help text below the controls.
        val infoY = brushSliderY + brushSliderH + 6
        val hit = PaintState.lastHit
        val text = if (hit != null) "${hit.part} (${hit.texelX}, ${hit.texelY})" else "no hit — aim at your character"
        graphics.text(this.font, text, controlsX, infoY, white, true)
        graphics.text(this.font, "L paint · R eye-drop · WASD orbit · scroll zoom · Esc", controlsX, infoY + 11, 0xFFBBBBBB.toInt(), true)
    }
}
