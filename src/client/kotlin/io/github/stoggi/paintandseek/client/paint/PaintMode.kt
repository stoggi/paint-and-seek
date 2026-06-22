package io.github.stoggi.paintandseek.client.paint

import io.github.stoggi.paintandseek.PaintAndSeek
import io.github.stoggi.paintandseek.client.skin.PaintedSkinTextures
import io.github.stoggi.paintandseek.net.SubmitSkinSnapshot
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft

/**
 * Client-side controller for "paint mode".
 *
 * Entering flips the camera to front third-person, seeds the painted skin from
 * the player's current skin (so the model stays visible), and opens the
 * non-pausing [PaintScreen]. Leaving restores the previous camera.
 */
object PaintMode {
    var active = false
        private set

    private var previousCamera: CameraType = CameraType.FIRST_PERSON

    fun toggle() {
        if (active) exit() else enter()
    }

    fun enter() {
        if (active) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        // Seed the painted skin from the real skin ONLY the first time - once a
        // painted skin exists, re-entering keeps the player's progress. The read
        // may be async (GPU readback for custom skins), so apply in the callback.
        if (!PaintedSkinTextures.has(player.uuid)) {
            val type = PaintState.modelType(mc)
            val uuid = player.uuid
            SkinSeed.seed(mc) { pixels ->
                clearOverlay(pixels, type) // start with a clean overlay layer
                PaintedSkinTextures.applySnapshot(uuid, pixels)
                ClientPlayNetworking.send(SubmitSkinSnapshot(pixels))
            }
        }

        PaintCamera.reset(player)
        previousCamera = mc.options.cameraType
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT)
        active = true
        mc.gui.setScreen(PaintScreen())
        PaintAndSeek.LOGGER.info("Entered paint mode")
    }

    /** Closes the paint screen (which restores the camera via [restoreCamera]). */
    fun exit() {
        if (!active) return
        val mc = Minecraft.getInstance()
        if (mc.gui.screen() is PaintScreen) {
            mc.gui.setScreen(null) // triggers PaintScreen.removed() -> restoreCamera()
        } else {
            restoreCamera()
        }
    }

    /** Make the overlay layer's texels transparent (default clean overlay). */
    private fun clearOverlay(pixels: IntArray, type: SkinModelType) {
        val mask = PlayerModelGeometry.layerMask(type, SkinLayer.OVERLAY)
        for (i in pixels.indices) {
            if (mask[i]) pixels[i] = 0
        }
    }

    /** Restore the pre-paint camera. Idempotent; safe to call from screen teardown. */
    fun restoreCamera() {
        if (!active) return
        Minecraft.getInstance().options.setCameraType(previousCamera)
        active = false
        PaintAndSeek.LOGGER.info("Exited paint mode")
    }
}
