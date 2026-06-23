package io.github.stoggi.paintandseek.client.paint

/** Rotation (radians) of a limb about its pivot, in ModelPart's ZYX convention. */
data class LimbRot(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    companion object {
        val NONE = LimbRot()
    }
}

private fun deg(d: Float): Float = Math.toRadians(d.toDouble()).toFloat()

/**
 * Upward nudge (blocks) for tipped ground poses. The model pivots about the feet, so
 * tipping it flat leaves the body's thickness straddling floor level (half embedded);
 * this lifts it to rest on top. The hitbox doesn't reposition the render, so this knob
 * is still needed. Scales with the entity (so it works at the 0.5 hider scale too).
 * Tune if poses float (reduce) or still sink (raise).
 */
private const val GROUND_LIFT = 0.15f

/**
 * Forward nudge (blocks) for tipped ground poses. Tipping about the feet lays the body
 * out entirely to one side, so the shadow/feet sit at one end instead of under the body.
 * This pulls the body back by ~half its length so it's centred on the entity (its
 * shadow). Negative = toward the feet; flip the sign if it shifts the body the wrong way.
 */
private const val GROUND_CENTER = -0.9f

/**
 * A selectable full-body pose. Poses rotate the limbs so painters can reach
 * otherwise-occluded surfaces (under the arms, inner legs, …) and to strike a
 * disguise stance. The pose is networked per-player so everyone sees it.
 *
 * The same limb rotations drive both the pick geometry ([PlayerModelGeometry])
 * and the rendered model (via the model mixin), so picking always matches.
 */
enum class PaintPose(
    val label: String,
    val rightArm: LimbRot = LimbRot.NONE,
    val leftArm: LimbRot = LimbRot.NONE,
    val rightLeg: LimbRot = LimbRot.NONE,
    val leftLeg: LimbRot = LimbRot.NONE,
    /**
     * Whole-body transform for ground poses, applied in-world only (not to the pick
     * geometry or the upright paint preview). [bodyPitch] tips the whole model about
     * its feet (radians); [bodyOffsetY]/[bodyOffsetZ] nudge it in blocks afterwards.
     */
    val bodyPitch: Float = 0f,
    val bodyOffsetY: Float = 0f,
    val bodyOffsetZ: Float = 0f,
) {
    DEFAULT("Default"),
    T_POSE("T-Pose", rightArm = LimbRot(z = deg(90f)), leftArm = LimbRot(z = deg(-90f))),
    ARMS_UP("Arms Up", rightArm = LimbRot(z = deg(165f)), leftArm = LimbRot(z = deg(-165f))),
    ARMS_FWD("Arms Fwd", rightArm = LimbRot(x = deg(-90f)), leftArm = LimbRot(x = deg(-90f))),
    LEGS_OUT(
        "Legs Out",
        rightArm = LimbRot(z = deg(18f)), leftArm = LimbRot(z = deg(-18f)),
        rightLeg = LimbRot(z = deg(22f)), leftLeg = LimbRot(z = deg(-22f)),
    ),
    STAR(
        "Star",
        rightArm = LimbRot(z = deg(125f)), leftArm = LimbRot(z = deg(-125f)),
        rightLeg = LimbRot(z = deg(20f)), leftLeg = LimbRot(z = deg(-20f)),
    ),
    // ---- ground poses: limbs arranged + whole body tipped flat (or lowered, for SIT) ----
    // GROUND_LIFT raises the tipped body so it rests on the floor instead of embedding.
    STARFISH(
        "Starfish",
        rightArm = LimbRot(z = deg(125f)), leftArm = LimbRot(z = deg(-125f)),
        rightLeg = LimbRot(z = deg(20f)), leftLeg = LimbRot(z = deg(-20f)),
        bodyPitch = deg(90f), bodyOffsetY = GROUND_LIFT, bodyOffsetZ = GROUND_CENTER,
    ),
    LIE_FLAT(
        "Lie Flat",
        // Arms together, straight along the body (default limb angles), tipped flat.
        bodyPitch = deg(90f), bodyOffsetY = GROUND_LIFT, bodyOffsetZ = GROUND_CENTER,
    ),
    BALL(
        "Ball",
        // Limbs curled toward the chest, then tipped onto the ground.
        rightArm = LimbRot(x = deg(-155f)), leftArm = LimbRot(x = deg(-155f)),
        rightLeg = LimbRot(x = deg(-150f)), leftLeg = LimbRot(x = deg(-150f)),
        bodyPitch = deg(90f), bodyOffsetY = GROUND_LIFT, bodyOffsetZ = GROUND_CENTER,
    ),
    SIT(
        "Sit",
        // Legs out in front, hands resting; body stays upright but lowered to the ground.
        rightArm = LimbRot(x = deg(-15f)), leftArm = LimbRot(x = deg(-15f)),
        rightLeg = LimbRot(x = deg(-90f)), leftLeg = LimbRot(x = deg(-90f)),
        bodyOffsetY = -0.42f,
    ),
    ;

    /** True if this pose applies an in-world whole-body transform (ground poses). */
    val hasBodyTransform: Boolean
        get() = bodyPitch != 0f || bodyOffsetY != 0f || bodyOffsetZ != 0f

    companion object {
        fun byId(id: Int): PaintPose = entries.getOrElse(id) { DEFAULT }
    }
}
