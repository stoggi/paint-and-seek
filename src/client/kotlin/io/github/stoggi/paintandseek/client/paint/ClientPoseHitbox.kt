package io.github.stoggi.paintandseek.client.paint

import net.minecraft.client.Minecraft
import java.util.UUID

/**
 * Client-side refresher for the pose-matched bounding box (see ClientEntityBoundingBoxMixin).
 *
 * The cached box only recomputes on movement/`refreshDimensions`, so when a stationary
 * player switches pose we force a refresh. The box is axis-aligned (yaw-independent), so
 * a turn alone needs no refresh - only a pose change does. Mirrors the server, which
 * refreshes on SubmitPose / round revert.
 */
object ClientPoseHitbox {
    private val lastPose = HashMap<UUID, Int>()

    fun tick() {
        val level = Minecraft.getInstance().level ?: return
        for (player in level.players()) {
            val poseId = ClientPoseStore.get(player.uuid).ordinal
            if (lastPose.put(player.uuid, poseId) != poseId) {
                player.refreshDimensions()
            }
        }
    }
}
