package io.paintjob.client.paint

import io.paintjob.Paintjob
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft

/**
 * Client-side controller for "paint mode".
 *
 * Entering flips the camera to front third-person so the player sees their own
 * character standing in the world; they then paint the skin directly on the
 * model and sample colours from the surrounding environment. Leaving restores
 * the previous camera.
 *
 * Pose-freeze, input capture and the painting overlay are layered on top of this
 * in subsequent steps.
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
        if (mc.player == null) return
        previousCamera = mc.options.cameraType
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT)
        active = true
        Paintjob.LOGGER.info("Entered paint mode")
    }

    fun exit() {
        if (!active) return
        Minecraft.getInstance().options.setCameraType(previousCamera)
        active = false
        Paintjob.LOGGER.info("Exited paint mode")
    }
}
