package io.paintandseek.client.paint

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side map of UUID -> selected [PaintPose], synced from the server. The
 * model mixin reads this for every rendered player so poses are visible to all.
 */
object ClientPoseStore {
    private val poses = ConcurrentHashMap<UUID, PaintPose>()

    fun get(uuid: UUID): PaintPose = poses[uuid] ?: PaintPose.DEFAULT

    fun set(uuid: UUID, pose: PaintPose) {
        if (pose == PaintPose.DEFAULT) poses.remove(uuid) else poses[uuid] = pose
    }

    fun clear(uuid: UUID) {
        poses.remove(uuid)
    }

    fun clearAll() {
        poses.clear()
    }
}
