package io.paintandseek.skin

import io.paintandseek.net.SkinRect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative, server-side store of every player's painted skin.
 *
 * The server keeps the full image so it can (a) apply incremental stroke patches
 * and (b) hand a complete [SkinImage] to any client that starts tracking a
 * painted player (late joiners, players walking into range).
 */
object ServerSkinStore {
    private val skins = ConcurrentHashMap<UUID, SkinImage>()

    fun get(uuid: UUID): SkinImage? = skins[uuid]

    fun has(uuid: UUID): Boolean = skins.containsKey(uuid)

    fun setSnapshot(uuid: UUID, pixels: IntArray) {
        skins[uuid] = SkinImage.of(pixels)
    }

    /** Apply a stroke patch, creating a blank base if the player has none yet. */
    fun applyPatch(uuid: UUID, rect: SkinRect) {
        val image = skins.getOrPut(uuid) { SkinImage.blank() }
        image.applyPatch(rect.x, rect.y, rect.w, rect.h, rect.pixels)
    }

    fun clear(uuid: UUID) {
        skins.remove(uuid)
    }

    fun clearAll() {
        skins.clear()
    }
}
