package io.paintjob.client.paint

import io.paintjob.client.skin.PaintedSkinTextures
import io.paintjob.net.SkinRect
import io.paintjob.net.SubmitSkinPatch
import io.paintjob.skin.SkinImage
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * The painting overlay. It does NOT pause the game, so the world (and the
 * player, in front third-person) keeps rendering behind it; freeing the cursor
 * lets us aim. Left-click / drag paints the skin texel under the cursor directly
 * on the model.
 *
 * This first version paints a solid [PaintState.colorArgb] with a debug readout
 * of the picked body part + texel for calibration. The colour wheel, eye-dropper
 * and stroke coalescing layer on top next.
 */
class PaintScreen : Screen(Component.literal("Paintjob")) {

    override fun isPauseScreen(): Boolean = false

    // Route to the in-game-UI background path (no menu blur)...
    override fun isInGameUi(): Boolean = true

    // ...and make that background draw nothing, so the world stays fully crisp
    // for colour-matching (the default in-game background darkens with a gradient).
    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) {
        // intentionally empty
    }

    override fun removed() {
        PaintMode.restoreCamera()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            paintAt(event.x(), event.y())
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (event.button() == 0) {
            paintAt(event.x(), event.y())
            return true
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    private fun paintAt(mouseX: Double, mouseY: Double) {
        val mc = minecraft
        val player = mc.player ?: return
        val type = PaintState.modelType(mc)
        val hit = PaintRaycaster.pick(mc, mouseX, mouseY, width, height, type)
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

        // Apply locally for instant feedback, then sync to the server.
        PaintedSkinTextures.applyPatch(player.uuid, rect)
        ClientPlayNetworking.send(SubmitSkinPatch(rect))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Live pick under the cursor (for the debug readout), without painting.
        val mc = minecraft
        PaintState.lastHit = PaintRaycaster.pick(mc, mouseX.toDouble(), mouseY.toDouble(), width, height, PaintState.modelType(mc))

        // Reticle.
        graphics.fill(mouseX - 6, mouseY, mouseX + 7, mouseY + 1, 0xFFFFFFFF.toInt())
        graphics.fill(mouseX, mouseY - 6, mouseX + 1, mouseY + 7, 0xFFFFFFFF.toInt())

        // Current colour swatch.
        graphics.fill(8, 8, 32, 32, 0xFF000000.toInt())
        graphics.fill(9, 9, 31, 31, PaintState.colorArgb)

        // Debug readout.
        val hit = PaintState.lastHit
        val text = if (hit != null) {
            "${hit.part} texel (${hit.texelX}, ${hit.texelY})  d=${"%.2f".format(hit.distance)}"
        } else {
            "no hit — aim at your character"
        }
        graphics.text(this.font, text, 40, 14, 0xFFFFFFFF.toInt(), true)
        graphics.text(this.font, "Left-click to paint · Esc to exit", 40, 26, 0xFFAAAAAA.toInt(), true)
    }
}
