package io.github.stoggi.paintandseek.game

import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Per-pose collision/hit box. Poses are otherwise render-only, so without this the
 * server keeps the default upright player column - the lying ground poses render well
 * outside it, so they clip through walls and spectral arrows miss the visible body.
 *
 * Like every vanilla entity box this is **axis-aligned and does NOT rotate with facing**
 * (no vanilla mob orients its hitbox to direction). It's a tight, fixed square footprint
 * + height per pose, centred on the entity and rising from the feet. The lying poses are
 * centred on the entity in the render (PaintPose.GROUND_CENTER), so a centred square sits
 * on the body regardless of which way they face.
 *
 * Only the poses whose silhouette leaves the normal column get a custom box; the rest
 * (upright poses, whose torso the vanilla column already covers) return null = vanilla.
 */
object PoseHitbox {
    /** Half-width (x and z) and height, in unscaled blocks. The entity scale applies on top. */
    private class Box(val halfWidth: Double, val height: Double)

    // Indexed by pose id (client PaintPose ordinal). null = use the vanilla box.
    private val BOXES: Array<Box?> = arrayOf(
        null, // 0 DEFAULT
        null, // 1 T_POSE   - upright, torso covered by the vanilla column
        null, // 2 ARMS_UP
        null, // 3 ARMS_FWD
        null, // 4 LEGS_OUT
        null, // 5 STAR
        Box(0.55, 0.55), // 6 STARFISH - lying, low + roughly square footprint
        Box(0.50, 0.55), // 7 LIE_FLAT
        Box(0.45, 0.55), // 8 BALL
        null, // 9 SIT     - compact + upright, vanilla column covers it
    )

    /** Tight axis-aligned box for [player]'s pose [poseId], or null to use the vanilla box. */
    fun boxFor(player: Player, position: Vec3, poseId: Int): AABB? {
        val box = BOXES.getOrNull(poseId) ?: return null
        val scale = player.scale.toDouble()
        val hw = box.halfWidth * scale
        val h = box.height * scale
        return AABB(
            position.x - hw, position.y, position.z - hw,
            position.x + hw, position.y + h, position.z + hw,
        )
    }
}
