package io.paintandseek.client.paint

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW

/**
 * Orbit-camera state for paint mode. The [CameraMixin] reads these to place the
 * camera around the player; WASD / arrow keys orbit and the scroll wheel zooms.
 *
 * The player's body stays put (model yaw is fixed while the screen is open), so
 * orbiting the camera lets you reach every side to paint, and the raycaster -
 * which reads the live camera - follows automatically.
 */
object PaintCamera {
    /** Camera look yaw/pitch (degrees) and orbit distance (blocks). */
    var yaw: Float = 0f
    var pitch: Float = 0f
    var distance: Float = 2.5f

    private const val MIN_DISTANCE = 1.0f
    private const val MAX_DISTANCE = 6.0f
    private const val ORBIT_SPEED = 3.0f // degrees per frame while held

    /** Start looking at the player's front (matches the old front-third-person view). */
    fun reset(player: Player) {
        yaw = player.yBodyRot + 180f
        pitch = 0f
        distance = 2.5f
    }

    fun zoom(scrollY: Double) {
        distance = (distance - scrollY.toFloat() * 0.3f).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /** Poll held orbit keys (called once per frame). */
    fun pollKeys(mc: Minecraft) {
        val window = mc.window
        if (down(window, GLFW.GLFW_KEY_A) || down(window, GLFW.GLFW_KEY_LEFT)) yaw += ORBIT_SPEED
        if (down(window, GLFW.GLFW_KEY_D) || down(window, GLFW.GLFW_KEY_RIGHT)) yaw -= ORBIT_SPEED
        if (down(window, GLFW.GLFW_KEY_W) || down(window, GLFW.GLFW_KEY_UP)) pitch += ORBIT_SPEED
        if (down(window, GLFW.GLFW_KEY_S) || down(window, GLFW.GLFW_KEY_DOWN)) pitch -= ORBIT_SPEED
        pitch = pitch.coerceIn(-89f, 89f)
    }

    private fun down(window: com.mojang.blaze3d.platform.Window, key: Int) =
        InputConstants.isKeyDown(window, key)
}
