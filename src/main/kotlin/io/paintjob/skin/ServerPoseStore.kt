package io.paintjob.skin

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative, server-side store of each player's selected pose (by id), so it
 * can be rebroadcast to everyone who can see them (and to late trackers).
 */
object ServerPoseStore {
    private val poses = ConcurrentHashMap<UUID, Int>()

    fun get(uuid: UUID): Int? = poses[uuid]

    fun set(uuid: UUID, poseId: Int) {
        if (poseId == 0) poses.remove(uuid) else poses[uuid] = poseId
    }

    fun clear(uuid: UUID) {
        poses.remove(uuid)
    }
}
