package io.paintjob.client.paint

import io.paintjob.skin.SkinImage
import org.joml.Vector3f

/** Wide ("default") or slim ("Alex") arm geometry. */
enum class SkinModelType { WIDE, SLIM }

/** Base skin layer, or the inflated overlay ("second layer": hat/jacket/sleeves/pants). */
enum class SkinLayer { BASE, OVERLAY }

enum class BodyPart { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

/** Restricts picking to one part group, so faces obscured by other parts stay reachable. */
enum class PartFilter(val label: String) {
    ALL("All"),
    HEAD("Head"),
    BODY("Body"),
    ARMS("Arms"),
    LEGS("Legs"),
    ;

    fun matches(part: BodyPart): Boolean = when (this) {
        ALL -> true
        HEAD -> part == BodyPart.HEAD
        BODY -> part == BodyPart.BODY
        ARMS -> part == BodyPart.RIGHT_ARM || part == BodyPart.LEFT_ARM
        LEGS -> part == BodyPart.RIGHT_LEG || part == BodyPart.LEFT_LEG
    }

    fun next(): PartFilter = entries[(ordinal + 1) % entries.size]
}

/** Result of a successful pick: which skin texel the cursor ray landed on. */
data class TexelHit(
    val part: BodyPart,
    val texelX: Int,
    val texelY: Int,
    val distance: Float,
)

/**
 * One axis-aligned quad face of a model cuboid, in model-pixel space (neutral
 * standing pose, all limb rotations zero — the pose we freeze while painting).
 *
 * Stored in the compact form the raycast needs: an origin corner plus the two
 * edge vectors, and the texel coordinates at those corners. The face is a
 * rectangle, so a hit point `P` solves to `P = origin + a*edgeU + b*edgeV` with
 * `a,b in [0,1]`, and the texel interpolates linearly from that.
 */
class ModelFace(
    val part: BodyPart,
    val origin: Vector3f,
    val edgeU: Vector3f,
    val edgeV: Vector3f,
    val normal: Vector3f,
    private val uA: Float,
    private val vA: Float,
    private val uB: Float,
    private val vB: Float,
) {
    private val edgeULenSq = edgeU.lengthSquared()
    private val edgeVLenSq = edgeV.lengthSquared()

    /** This face's texel rectangle (skin-pixel bounds), half-open. */
    val texMinX = minOf(uA, uB).toInt()
    val texMaxX = maxOf(uA, uB).toInt()
    val texMinY = minOf(vA, vB).toInt()
    val texMaxY = maxOf(vA, vB).toInt()

    /**
     * Intersect a ray (in model space) with this face. Returns the hit distance
     * along [dir], or null if the ray misses the quad or hits its back side.
     */
    fun intersect(origin: Vector3f, dir: Vector3f): Float? {
        val denom = dir.dot(normal)
        if (denom >= -1.0e-6f) return null // parallel or back-facing
        val t = Vector3f(this.origin).sub(origin).dot(normal) / denom
        if (t <= 0f) return null
        val px = origin.x + dir.x * t
        val py = origin.y + dir.y * t
        val pz = origin.z + dir.z * t
        val rx = px - this.origin.x
        val ry = py - this.origin.y
        val rz = pz - this.origin.z
        val a = (rx * edgeU.x + ry * edgeU.y + rz * edgeU.z) / edgeULenSq
        val b = (rx * edgeV.x + ry * edgeV.y + rz * edgeV.z) / edgeVLenSq
        if (a < 0f || a > 1f || b < 0f || b > 1f) return null
        return t
    }

    /** Texel (skin pixel) hit by parameters ([a], [b]) within this face. */
    fun texel(origin: Vector3f, dir: Vector3f, t: Float): Pair<Int, Int> {
        val px = origin.x + dir.x * t
        val py = origin.y + dir.y * t
        val pz = origin.z + dir.z * t
        val rx = px - this.origin.x
        val ry = py - this.origin.y
        val rz = pz - this.origin.z
        val a = (rx * edgeU.x + ry * edgeU.y + rz * edgeU.z) / edgeULenSq
        val b = (rx * edgeV.x + ry * edgeV.y + rz * edgeV.z) / edgeVLenSq
        val u = uA + a * (uB - uA)
        val v = vA + b * (vB - vA)
        // Clamp into the face's texel rect [min, max) so edge hits stay in-bounds.
        val uMin = minOf(uA, uB)
        val uMax = maxOf(uA, uB)
        val vMin = minOf(vA, vB)
        val vMax = maxOf(vA, vB)
        val tx = u.toInt().coerceIn(uMin.toInt(), (uMax - 1).toInt().coerceAtLeast(uMin.toInt()))
        val ty = v.toInt().coerceIn(vMin.toInt(), (vMax - 1).toInt().coerceAtLeast(vMin.toInt()))
        return tx to ty
    }
}

object PlayerModelGeometry {
    private data class Key(val type: SkinModelType, val layer: SkinLayer, val pose: PaintPose)

    private val cache = HashMap<Key, List<ModelFace>>()
    private val maskCache = HashMap<Key, BooleanArray>()

    fun faces(type: SkinModelType, layer: SkinLayer, pose: PaintPose): List<ModelFace> =
        cache.getOrPut(Key(type, layer, pose)) { buildModel(type, layer, pose) }

    /**
     * Texels (64x64) owned by the given [layer]'s faces (texel ownership doesn't
     * depend on pose). Used to keep an eraser stroke from clearing texels that
     * belong to the other layer — so painting transparency on the overlay can
     * never clear the base skin — and to confine painting to the selected layer.
     */
    fun layerMask(type: SkinModelType, layer: SkinLayer): BooleanArray =
        maskCache.getOrPut(Key(type, layer, PaintPose.DEFAULT)) {
            val mask = BooleanArray(SkinImage.WIDTH * SkinImage.HEIGHT)
            for (face in faces(type, layer, PaintPose.DEFAULT)) {
                for (ty in face.texMinY until face.texMaxY) {
                    for (tx in face.texMinX until face.texMaxX) {
                        if (tx in 0 until SkinImage.WIDTH && ty in 0 until SkinImage.HEIGHT) {
                            mask[ty * SkinImage.WIDTH + tx] = true
                        }
                    }
                }
            }
            mask
        }

    /**
     * Cast a ray (model space, neutral pose) against the chosen skin [layer] and
     * return the nearest front-facing skin texel, or null on a miss.
     */
    fun raycast(
        rayOrigin: Vector3f,
        rayDir: Vector3f,
        type: SkinModelType,
        layer: SkinLayer,
        pose: PaintPose,
        filter: PartFilter,
    ): TexelHit? {
        var bestT = Float.MAX_VALUE
        var bestFace: ModelFace? = null
        for (face in faces(type, layer, pose)) {
            if (!filter.matches(face.part)) continue // ignore parts in front when isolating
            val t = face.intersect(rayOrigin, rayDir) ?: continue
            if (t < bestT) {
                bestT = t
                bestFace = face
            }
        }
        val face = bestFace ?: return null
        val (tx, ty) = face.texel(rayOrigin, rayDir, bestT)
        return TexelHit(face.part, tx, ty, bestT)
    }

    private fun buildModel(type: SkinModelType, layer: SkinLayer, pose: PaintPose): List<ModelFace> {
        val armW = if (type == SkinModelType.SLIM) 3f else 4f
        val faces = ArrayList<ModelFace>(36)
        // part, texU, texV, box origin (relative to pivot), w,h,d, pivot, grow, limb rotation
        if (layer == SkinLayer.BASE) {
            box(faces, BodyPart.HEAD, 0, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0f, 0f, 0f)
            box(faces, BodyPart.BODY, 16, 16, -4f, 0f, -2f, 8f, 12f, 4f, 0f, 0f, 0f)
            box(faces, BodyPart.RIGHT_ARM, 40, 16, -armW + 1f, -2f, -2f, armW, 12f, 4f, -5f, 2f, 0f, 0f, pose.rightArm)
            box(faces, BodyPart.LEFT_ARM, 32, 48, -1f, -2f, -2f, armW, 12f, 4f, 5f, 2f, 0f, 0f, pose.leftArm)
            box(faces, BodyPart.RIGHT_LEG, 0, 16, -2f, 0f, -2f, 4f, 12f, 4f, -1.9f, 12f, 0f, 0f, pose.rightLeg)
            box(faces, BodyPart.LEFT_LEG, 16, 48, -2f, 0f, -2f, 4f, 12f, 4f, 1.9f, 12f, 0f, 0f, pose.leftLeg)
        } else {
            // Overlay ("second layer"): hat/jacket/sleeves/pants, slightly inflated.
            box(faces, BodyPart.HEAD, 32, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0f, 0f, 0f, 0.5f)
            box(faces, BodyPart.BODY, 16, 32, -4f, 0f, -2f, 8f, 12f, 4f, 0f, 0f, 0f, 0.25f)
            box(faces, BodyPart.RIGHT_ARM, 40, 32, -armW + 1f, -2f, -2f, armW, 12f, 4f, -5f, 2f, 0f, 0.25f, pose.rightArm)
            box(faces, BodyPart.LEFT_ARM, 48, 48, -1f, -2f, -2f, armW, 12f, 4f, 5f, 2f, 0f, 0.25f, pose.leftArm)
            box(faces, BodyPart.RIGHT_LEG, 0, 32, -2f, 0f, -2f, 4f, 12f, 4f, -1.9f, 12f, 0f, 0.25f, pose.rightLeg)
            box(faces, BodyPart.LEFT_LEG, 0, 48, -2f, 0f, -2f, 4f, 12f, 4f, 1.9f, 12f, 0f, 0.25f, pose.leftLeg)
        }
        return faces
    }

    @Suppress("LongParameterList")
    private fun box(
        out: MutableList<ModelFace>,
        part: BodyPart,
        texU: Int,
        texV: Int,
        ox: Float, oy: Float, oz: Float,
        w: Float, h: Float, d: Float,
        pivotX: Float, pivotY: Float, pivotZ: Float,
        grow: Float = 0f,
        rot: LimbRot = LimbRot.NONE,
    ) {
        // Geometry is inflated by [grow] (overlay layers), but the UV unwrap still
        // uses the original w/h/d — exactly as ModelPart.Cube does. Corners are
        // built relative to the pivot, rotated by [rot] (ModelPart's ZYX order),
        // then translated to the pivot — matching the rendered posed model.
        val lminX = ox - grow
        val lminY = oy - grow
        val lminZ = oz - grow
        val lmaxX = ox + w + grow
        val lmaxY = oy + h + grow
        val lmaxZ = oz + d + grow

        val q = if (rot == LimbRot.NONE) null else org.joml.Quaternionf().rotationZYX(rot.z, rot.y, rot.x)
        val pivot = Vector3f(pivotX, pivotY, pivotZ)
        fun v(x: Float, y: Float, z: Float): Vector3f {
            val p = Vector3f(x, y, z)
            q?.transform(p)
            return p.add(pivot)
        }
        val t0 = v(lminX, lminY, lminZ)
        val t1 = v(lmaxX, lminY, lminZ)
        val t2 = v(lmaxX, lmaxY, lminZ)
        val t3 = v(lminX, lmaxY, lminZ)
        val l0 = v(lminX, lminY, lmaxZ)
        val l1 = v(lmaxX, lminY, lmaxZ)
        val l2 = v(lmaxX, lmaxY, lmaxZ)
        val l3 = v(lminX, lmaxY, lmaxZ)

        fun n(x: Float, y: Float, z: Float): Vector3f {
            val nrm = Vector3f(x, y, z)
            q?.transform(nrm)
            return nrm
        }

        val u0 = texU.toFloat()
        val u1 = texU + d
        val u2 = texU + d + w
        val u22 = texU + d + w + w
        val u3 = texU + d + w + d
        val u4 = texU + d + w + d + w
        val v0 = texV.toFloat()
        val v1 = texV + d
        val v2 = texV + d + h

        // face(verts p0,p1,p2,p3 ; uvRect uA,vA,uB,vB ; outward normal) — texel
        // mapping: p0->(uB,vA) p1->(uA,vA) p2->(uA,vB) p3->(uB,vB)
        out += face(part, l1, l0, t0, t1, u1, v0, u2, v1, n(0f, -1f, 0f)) // DOWN  (-Y)
        out += face(part, t2, t3, l3, l2, u2, v1, u22, v0, n(0f, 1f, 0f)) // UP    (+Y)
        out += face(part, t0, l0, l3, t3, u0, v1, u1, v2, n(-1f, 0f, 0f)) // WEST  (-X)
        out += face(part, t1, t0, t3, t2, u1, v1, u2, v2, n(0f, 0f, -1f)) // NORTH (-Z, front)
        out += face(part, l1, t1, t2, l2, u2, v1, u3, v2, n(1f, 0f, 0f)) // EAST   (+X)
        out += face(part, l0, l1, l2, l3, u3, v1, u4, v2, n(0f, 0f, 1f)) // SOUTH  (+Z, back)
    }

    /** Build a face from its 4 corners (p0..p3), texel rect, and outward normal. */
    @Suppress("LongParameterList")
    private fun face(
        part: BodyPart,
        p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f,
        uA: Float, vA: Float, uB: Float, vB: Float,
        normal: Vector3f,
    ): ModelFace {
        // Parallelogram with p1 as origin: p0 = p1 + edgeU, p2 = p1 + edgeV.
        val edgeU = Vector3f(p0).sub(p1)
        val edgeV = Vector3f(p2).sub(p1)
        return ModelFace(part, Vector3f(p1), edgeU, edgeV, normal, uA, vA, uB, vB)
    }
}
