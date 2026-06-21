package io.paintandseek.client.paint

/** Rotation (radians) of a limb about its pivot, in ModelPart's ZYX convention. */
data class LimbRot(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    companion object {
        val NONE = LimbRot()
    }
}

private fun deg(d: Float): Float = Math.toRadians(d.toDouble()).toFloat()

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
    ;

    companion object {
        fun byId(id: Int): PaintPose = entries.getOrElse(id) { DEFAULT }
    }
}
