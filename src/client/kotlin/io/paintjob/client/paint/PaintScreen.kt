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
import kotlin.math.tan

/**
 * The painting overlay. Does NOT pause the game and draws no background, so the
 * world + the player (orbit camera) stay crisp behind it.
 *
 * Controls:
 *  - Left-click / drag on the model: paint the picked texel.
 *  - Right-click anywhere: eye-dropper — sample the on-screen pixel as the colour.
 *  - Colour wheel + value slider + transparent swatch (bottom-left).
 *  - Layer toggle + brush size slider (right side). WASD/arrows orbit, scroll zooms.
 */
class PaintScreen : Screen(Component.literal("Paintjob")) {

    // Colour picker — bottom-left.
    private val wheelX get() = 12
    private val wheelY get() = height - 12 - ColorWheel.SIZE
    private val valueSliderX get() = wheelX + ColorWheel.SIZE + 10
    private val valueSliderY get() = wheelY
    private val valueSliderW = 14
    private val valueSliderH get() = ColorWheel.SIZE
    private val swatchX get() = wheelX
    private val swatchY get() = wheelY - 28
    private val swatchSize = 24
    private val transX get() = swatchX + swatchSize + 6
    private val transY get() = swatchY
    private val transSize = 24

    // Controls — right side.
    private val controlW = 130
    private val controlX get() = width - 12 - controlW
    private val toggleX get() = controlX
    private val toggleY get() = height - 12 - 18
    private val toggleW get() = controlW
    private val toggleH = 18
    private val brushSliderX get() = controlX
    private val brushSliderY get() = toggleY - 24
    private val brushSliderW get() = controlW
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
                if (inRect(x, y, swatchX, swatchY, swatchSize, swatchSize)) {
                    if (isClick) PaintState.selectColor()
                    return true
                }
                if (inRect(x, y, transX, transY, transSize, transSize)) {
                    if (isClick) PaintState.selectTransparent()
                    return true
                }
                if (inRect(x, y, brushSliderX, brushSliderY, brushSliderW, brushSliderH)) {
                    val frac = ((x - brushSliderX) / brushSliderW).coerceIn(0.0, 1.0)
                    PaintState.brushRadius = (frac * PaintState.MAX_BRUSH_RADIUS).roundToInt()
                    return true
                }
                if (inRect(x, y, toggleX, toggleY, toggleW, toggleH)) {
                    if (isClick) PaintState.toggleLayer()
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
            inRect(x, y, swatchX, swatchY, swatchSize, swatchSize) ||
            inRect(x, y, transX, transY, transSize, transSize) ||
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
        val color = PaintState.effectivePaintColor()
        // When erasing the overlay, restrict to overlay-owned texels so a stroke
        // can never clear texels that belong to the base skin.
        val erasing = PaintState.transparentMode && PaintState.layer == SkinLayer.OVERLAY
        val mask = if (erasing) PlayerModelGeometry.layerMask(PaintState.modelType(mc), SkinLayer.OVERLAY) else null
        val pixels = IntArray(w * h) { idx ->
            val gx = x0 + idx % w
            val gy = y0 + idx / w
            if (mask == null || mask[gy * SkinImage.WIDTH + gx]) color
            else PaintedSkinTextures.pixel(player.uuid, gx, gy)
        }
        val rect = SkinRect(x0, y0, w, h, pixels)

        PaintedSkinTextures.applyPatch(player.uuid, rect)
        ClientPlayNetworking.send(SubmitSkinPatch(rect))
    }

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

        PaintCamera.pollKeys(mc)

        val overUi = overUi(mouseX.toDouble(), mouseY.toDouble())
        if (!overUi) {
            PaintState.lastHit = PaintRaycaster.pick(mc, mouseX.toDouble(), mouseY.toDouble(), width, height, PaintState.modelType(mc), PaintState.layer)
        }

        // Brush footprint highlight on the model (sized to brush + zoom).
        if (!overUi && PaintState.lastHit != null) {
            renderBrushHighlight(graphics, mc, mouseX, mouseY)
        }

        // Reticle with a centre gap, so eye-dropping samples the world, not the reticle.
        val white = 0xFFFFFFFF.toInt()
        graphics.fill(mouseX - 8, mouseY, mouseX - 2, mouseY + 1, white)
        graphics.fill(mouseX + 3, mouseY, mouseX + 9, mouseY + 1, white)
        graphics.fill(mouseX, mouseY - 8, mouseX + 1, mouseY - 2, white)
        graphics.fill(mouseX, mouseY + 3, mouseX + 1, mouseY + 9, white)

        renderColorPicker(graphics)
        renderControls(graphics)
        renderHud(graphics)
    }

    private fun renderColorPicker(graphics: GuiGraphicsExtractor) {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val select = 0xFFFFD24A.toInt()

        // Current colour swatch (selected border when not transparent).
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchSize + 1, swatchY + swatchSize + 1, if (!PaintState.transparentMode) select else black)
        graphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, PaintState.colorArgb)

        // Transparent ("eraser") checker swatch — only meaningful on the overlay.
        val transUsable = PaintState.layer == SkinLayer.OVERLAY
        graphics.fill(transX - 1, transY - 1, transX + transSize + 1, transY + transSize + 1, if (PaintState.transparentMode) select else black)
        drawChecker(graphics, transX, transY, transSize, if (transUsable) 0xFFFFFFFF.toInt() else 0xFF777777.toInt(), if (transUsable) 0xFFB0B0B0.toInt() else 0xFF555555.toInt())

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

        // Brush size slider.
        val diameter = PaintState.brushRadius * 2 + 1
        graphics.text(this.font, "Brush: ${diameter}px", brushSliderX, brushSliderY - 11, white, true)
        graphics.fill(brushSliderX - 1, brushSliderY - 1, brushSliderX + brushSliderW + 1, brushSliderY + brushSliderH + 1, black)
        graphics.fill(brushSliderX, brushSliderY, brushSliderX + brushSliderW, brushSliderY + brushSliderH, grey)
        val frac = PaintState.brushRadius.toFloat() / PaintState.MAX_BRUSH_RADIUS
        val knobX = brushSliderX + (frac * brushSliderW).toInt()
        graphics.fill(knobX - 2, brushSliderY - 2, knobX + 2, brushSliderY + brushSliderH + 2, accent)

        // Layer toggle button.
        graphics.fill(toggleX - 1, toggleY - 1, toggleX + toggleW + 1, toggleY + toggleH + 1, black)
        graphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, grey)
        val layerName = if (PaintState.layer == SkinLayer.OVERLAY) "Overlay" else "Base"
        graphics.text(this.font, "Layer: $layerName", toggleX + 6, toggleY + 5, white, true)
    }

    /** A square outline at the cursor showing the brush footprint on the model. */
    private fun renderBrushHighlight(graphics: GuiGraphicsExtractor, mc: net.minecraft.client.Minecraft, mouseX: Int, mouseY: Int) {
        val fovRad = Math.toRadians(mc.gameRenderer.mainCamera().fov.toDouble())
        val pxPerBlock = height / (2.0 * PaintCamera.distance * tan(fovRad / 2.0))
        val texelPx = pxPerBlock / 16.0 // a skin texel is 1/16 block
        val half = ((PaintState.brushRadius + 0.5) * texelPx).toInt().coerceIn(2, 400)
        val preview = if (PaintState.transparentMode) 0xFFFFFFFF.toInt() else (PaintState.colorArgb or (0xFF shl 24))
        squareOutline(graphics, mouseX, mouseY, half, 0xFF000000.toInt())
        squareOutline(graphics, mouseX, mouseY, half - 1, preview)
    }

    private fun squareOutline(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, half: Int, color: Int) {
        if (half < 1) return
        graphics.fill(cx - half, cy - half, cx + half, cy - half + 1, color)
        graphics.fill(cx - half, cy + half - 1, cx + half, cy + half, color)
        graphics.fill(cx - half, cy - half, cx - half + 1, cy + half, color)
        graphics.fill(cx + half - 1, cy - half, cx + half, cy + half, color)
    }

    private fun renderHud(graphics: GuiGraphicsExtractor) {
        val white = 0xFFFFFFFF.toInt()
        val hit = PaintState.lastHit
        val text = if (hit != null) "${hit.part} (${hit.texelX}, ${hit.texelY})" else "no hit — aim at your character"
        graphics.text(this.font, text, 12, 12, white, true)
        graphics.text(this.font, "L paint · R eye-drop · WASD orbit · scroll zoom · Esc", 12, 24, 0xFFBBBBBB.toInt(), true)
    }

    /** Draw a checkerboard (transparency indicator) in the box at ([x],[y]). */
    private fun drawChecker(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, light: Int, dark: Int) {
        val cell = 4
        var j = 0
        var py = y
        while (py < y + size) {
            var i = 0
            var px = x
            while (px < x + size) {
                val x1 = minOf(px + cell, x + size)
                val y1 = minOf(py + cell, y + size)
                graphics.fill(px, py, x1, y1, if ((i + j) % 2 == 0) light else dark)
                px += cell
                i++
            }
            py += cell
            j++
        }
    }
}
