package io.paintjob.client.paint

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Turns a cursor position into the skin texel it's pointing at on the local
 * player's model.
 *
 * Two transforms compose:
 *  1. cursor -> world ray, built from the camera's own near-plane corners (so it
 *     matches the engine's projection exactly, no sign-guessing).
 *  2. world -> model-pixel space, the inverse of the entity render transform
 *     (`Ry(180-bodyRot) · flip(-1,-1,1) · scale(0.9375) · translate(0,-1.501,0)
 *     · scale(1/16)`), applied entity-relative to keep float precision.
 *
 * Then [PlayerModelGeometry.raycast] resolves the texel. Assumes the painting
 * pose is the neutral standing pose (which holds while the paint screen is open,
 * since the player isn't turning to look around).
 */
object PaintRaycaster {
    fun pick(
        mc: Minecraft,
        mouseX: Double,
        mouseY: Double,
        screenW: Int,
        screenH: Int,
        type: SkinModelType,
        layer: SkinLayer,
        pose: PaintPose,
    ): TexelHit? {
        val player = mc.player ?: return null
        val camera = mc.gameRenderer.mainCamera()
        if (!camera.isInitialized) return null

        // 1. Cursor -> world ray via the near-plane corners.
        val plane = camera.getNearPlane(camera.fov)
        val u = (mouseX / screenW).coerceIn(0.0, 1.0)
        val v = (mouseY / screenH).coerceIn(0.0, 1.0)
        val top = lerp(plane.topLeft, plane.topRight, u)
        val bottom = lerp(plane.bottomLeft, plane.bottomRight, u)
        val planePoint = lerp(top, bottom, v) // offset from camera position
        val camPos = camera.position()

        // 2. Build world->model (entity-relative) and apply.
        val bodyRot = Math.toRadians((180.0f - player.yBodyRot).toDouble()).toFloat()
        val world = Matrix4f()
            .rotateY(bodyRot)
            .scale(-1.0f, -1.0f, 1.0f)
            .scale(0.9375f)
            .translate(0.0f, -1.501f, 0.0f)
            .scale(1.0f / 16.0f)
        val inv = world.invert(Matrix4f())

        val relOrigin = Vector3f(
            (camPos.x - player.x).toFloat(),
            (camPos.y - player.y).toFloat(),
            (camPos.z - player.z).toFloat(),
        )
        val rayOrigin = inv.transformPosition(relOrigin, Vector3f())
        val rayDir = inv.transformDirection(
            Vector3f(planePoint.x.toFloat(), planePoint.y.toFloat(), planePoint.z.toFloat()),
            Vector3f(),
        ).normalize()

        return PlayerModelGeometry.raycast(rayOrigin, rayDir, type, layer, pose)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Double): Vec3 =
        Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
}
