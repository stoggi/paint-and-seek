package io.paintjob.client.paint

import io.paintjob.client.skin.PaintedSkinTextures
import io.paintjob.net.SkinRect
import io.paintjob.net.SubmitPose
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
    private val toggleW get() = (controlW - 4) / 2
    private val toggleH = 18
    private val isolateX get() = toggleX + toggleW + 4
    private val isolateY get() = toggleY
    private val isolateW get() = controlW - toggleW - 4
    private val brushSliderX get() = controlX
    private val brushSliderY get() = toggleY - 24
    private val brushSliderW get() = controlW
    private val brushSliderH = 10

    // Pose buttons — a 2x3 grid above the brush controls.
    private val poseColW get() = (controlW - 4) / 2
    private val poseRowStep = 18
    private val poseBtnH = 16
    private val poseGridTop get() = brushSliderY - 16 - 3 * poseRowStep

    private fun poseX(i: Int) = controlX + (i % 2) * (poseColW + 4)
    private fun poseY(i: Int) = poseGridTop + (i / 2) * poseRowStep
    private val poseGridH get() = 3 * poseRowStep

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
                    PaintState.brushSize = 1 + (frac * (PaintState.MAX_BRUSH_SIZE - 1)).roundToInt()
                    return true
                }
                if (inRect(x, y, toggleX, toggleY, toggleW, toggleH)) {
                    if (isClick) PaintState.toggleLayer()
                    return true
                }
                if (inRect(x, y, isolateX, isolateY, isolateW, toggleH)) {
                    if (isClick) PaintState.partFilter = PaintState.partFilter.next()
                    return true
                }
                for (i in PaintPose.entries.indices) {
                    if (inRect(x, y, poseX(i), poseY(i), poseColW, poseBtnH)) {
                        if (isClick) selectPose(PaintPose.entries[i])
                        return true
                    }
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
            inRect(x, y, controlX, toggleY, controlW, toggleH) ||
            inRect(x, y, controlX, poseGridTop, controlW, poseGridH)

    /** Select a pose: drive picking, show it locally at once, and sync to others. */
    private fun selectPose(pose: PaintPose) {
        PaintState.pose = pose
        val player = minecraft.player ?: return
        ClientPoseStore.set(player.uuid, pose)
        ClientPlayNetworking.send(SubmitPose(pose.ordinal))
    }

    private fun paintAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val player = mc.player ?: return
        val type = PaintState.modelType(mc)
        val layer = PaintState.layer
        val pose = PaintState.pose
        val filter = PaintState.partFilter
        PaintState.lastHit = PaintRaycaster.pick(mc, mouseX, mouseY, width, height, type, layer, pose, filter)

        // Sample a grid of rays across the brush footprint (half-texel resolution)
        // and, for each, paint EVERY camera-facing texel the ray passes through —
        // so occluded-but-visible surfaces (e.g. inner arm behind the body) under
        // the brush get painted too.
        val size = PaintState.brushSize
        val texelPx = texelScreenPx(mc)
        val half = if (size <= 1) 0.0 else (size / 2.0) * texelPx
        val samplesPerAxis = if (size <= 1) 1 else size * 2
        val mask = PlayerModelGeometry.layerMask(type, layer)

        val hits = HashSet<Int>()
        for (iy in 0 until samplesPerAxis) {
            val sy = if (samplesPerAxis == 1) 0.0 else -half + iy.toDouble() / (samplesPerAxis - 1) * 2 * half
            for (ix in 0 until samplesPerAxis) {
                val sx = if (samplesPerAxis == 1) 0.0 else -half + ix.toDouble() / (samplesPerAxis - 1) * 2 * half
                val (origin, dir) = PaintRaycaster.modelRay(mc, mouseX + sx, mouseY + sy, width, height) ?: continue
                PlayerModelGeometry.raycastAllTexels(origin, dir, type, layer, pose, filter, mask, hits)
            }
        }
        if (hits.isEmpty()) return

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (idx in hits) {
            val gx = idx % SkinImage.WIDTH
            val gy = idx / SkinImage.WIDTH
            if (gx < minX) minX = gx
            if (gx > maxX) maxX = gx
            if (gy < minY) minY = gy
            if (gy > maxY) maxY = gy
        }

        val color = PaintState.effectivePaintColor()
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        val pixels = IntArray(w * h) { idx ->
            val gx = minX + idx % w
            val gy = minY + idx / w
            if (hits.contains(gy * SkinImage.WIDTH + gx)) color
            else PaintedSkinTextures.pixel(player.uuid, gx, gy)
        }
        val rect = SkinRect(minX, minY, w, h, pixels)

        PaintedSkinTextures.applyPatch(player.uuid, rect)
        ClientPlayNetworking.send(SubmitSkinPatch(rect))
    }

    /** Approx screen size (in GUI px) of one skin texel on the model at the current zoom. */
    private fun texelScreenPx(mc: net.minecraft.client.Minecraft): Double {
        val fovRad = Math.toRadians(mc.gameRenderer.mainCamera().fov.toDouble())
        val pxPerBlock = height / (2.0 * PaintCamera.distance * tan(fovRad / 2.0))
        return pxPerBlock / 16.0 // a skin texel is 1/16 block
    }

    private fun eyedropAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val target = mc.gameRenderer.mainRenderTarget()
        val sw = width.toDouble()
        val sh = height.toDouble()
        // The framebuffer pixel is already darkened by world lighting; recover an
        // approximate albedo by dividing out the local light level, so the painted
        // skin (which gets re-lit) matches what was sampled instead of going double-dark.
        val player = mc.player
        val light = if (player != null) mc.level?.getMaxLocalRawBrightness(player.blockPosition()) ?: 15 else 15
        val brightness = (light / 15.0).coerceIn(0.25, 1.0)
        Screenshot.takeScreenshot(target) { image ->
            val fx = (mouseX / sw * image.width).toInt().coerceIn(0, image.width - 1)
            val fy = (mouseY / sh * image.height).toInt().coerceIn(0, image.height - 1)
            val argb = image.getPixel(fx, fy)
            val r = (((argb ushr 16) and 0xFF) / brightness).toInt().coerceAtMost(255)
            val g = (((argb ushr 8) and 0xFF) / brightness).toInt().coerceAtMost(255)
            val b = ((argb and 0xFF) / brightness).toInt().coerceAtMost(255)
            PaintState.setFromArgb((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            image.close()
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = minecraft

        PaintCamera.pollKeys(mc)

        val overUi = overUi(mouseX.toDouble(), mouseY.toDouble())
        if (!overUi) {
            PaintState.lastHit = PaintRaycaster.pick(mc, mouseX.toDouble(), mouseY.toDouble(), width, height, PaintState.modelType(mc), PaintState.layer, PaintState.pose, PaintState.partFilter)
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
        renderPoses(graphics)
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
        graphics.text(this.font, "Brush: ${PaintState.brushSize}px", brushSliderX, brushSliderY - 11, white, true)
        graphics.fill(brushSliderX - 1, brushSliderY - 1, brushSliderX + brushSliderW + 1, brushSliderY + brushSliderH + 1, black)
        graphics.fill(brushSliderX, brushSliderY, brushSliderX + brushSliderW, brushSliderY + brushSliderH, grey)
        val frac = (PaintState.brushSize - 1).toFloat() / (PaintState.MAX_BRUSH_SIZE - 1)
        val knobX = brushSliderX + (frac * brushSliderW).toInt()
        graphics.fill(knobX - 2, brushSliderY - 2, knobX + 2, brushSliderY + brushSliderH + 2, accent)

        // Layer toggle (left) + part isolate (right).
        val layerName = if (PaintState.layer == SkinLayer.OVERLAY) "Overlay" else "Base"
        graphics.fill(toggleX - 1, toggleY - 1, toggleX + toggleW + 1, toggleY + toggleH + 1, black)
        graphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, grey)
        graphics.text(this.font, layerName, toggleX + 5, toggleY + 5, white, true)

        graphics.fill(isolateX - 1, toggleY - 1, isolateX + isolateW + 1, toggleY + toggleH + 1, black)
        graphics.fill(isolateX, toggleY, isolateX + isolateW, toggleY + toggleH, grey)
        graphics.text(this.font, "Only: ${PaintState.partFilter.label}", isolateX + 5, toggleY + 5, white, true)
    }

    /** A square outline at the cursor showing the brush footprint on the model. */
    private fun renderBrushHighlight(graphics: GuiGraphicsExtractor, mc: net.minecraft.client.Minecraft, mouseX: Int, mouseY: Int) {
        val half = ((PaintState.brushSize / 2.0) * texelScreenPx(mc)).toInt().coerceIn(2, 400)
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

    private fun renderPoses(graphics: GuiGraphicsExtractor) {
        val black = 0xFF000000.toInt()
        val grey = 0xFF555555.toInt()
        val selected = 0xFF3A6B33.toInt()
        val white = 0xFFFFFFFF.toInt()
        graphics.text(this.font, "Pose", controlX, poseGridTop - 11, white, true)
        for (i in PaintPose.entries.indices) {
            val pose = PaintPose.entries[i]
            val x = poseX(i)
            val y = poseY(i)
            graphics.fill(x - 1, y - 1, x + poseColW + 1, y + poseBtnH + 1, black)
            graphics.fill(x, y, x + poseColW, y + poseBtnH, if (PaintState.pose == pose) selected else grey)
            graphics.text(this.font, pose.label, x + 4, y + 4, white, true)
        }
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
