package io.paintjob.net

import io.paintjob.skin.ServerSkinStore
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * Registers paintjob's custom packets and the server-side handlers that keep the
 * authoritative [ServerSkinStore] up to date and rebroadcast changes to everyone
 * who can see the painter.
 *
 * Payload *types* must be registered on both physical sides; the receivers below
 * are server-only. The client receivers live in the client entrypoint.
 */
object PaintNetworking {
    /** Registers every payload type. Safe to call on both client and dedicated server. */
    fun registerPayloads() {
        val c2s = PayloadTypeRegistry.serverboundPlay()
        c2s.register(SubmitSkinPatch.TYPE, SubmitSkinPatch.CODEC)
        c2s.register(SubmitSkinSnapshot.TYPE, SubmitSkinSnapshot.CODEC)

        val s2c = PayloadTypeRegistry.clientboundPlay()
        s2c.register(SkinPatch.TYPE, SkinPatch.CODEC)
        s2c.register(SkinSnapshot.TYPE, SkinSnapshot.CODEC)
        s2c.register(ClearSkin.TYPE, ClearSkin.CODEC)
    }

    fun registerServerHandlers() {
        // A hider painted a stroke: store it and forward the dirty rect to viewers.
        ServerPlayNetworking.registerGlobalReceiver(SubmitSkinPatch.TYPE) { payload, ctx ->
            val player = ctx.player()
            ServerSkinStore.applyPatch(player.uuid, payload.rect)
            broadcastToViewers(player, SkinPatch(player.uuid, payload.rect))
        }

        // Full upload (on open / commit): replace the stored image and forward it.
        ServerPlayNetworking.registerGlobalReceiver(SubmitSkinSnapshot.TYPE) { payload, ctx ->
            val player = ctx.player()
            ServerSkinStore.setSnapshot(player.uuid, payload.pixels)
            broadcastToViewers(player, SkinSnapshot(player.uuid, payload.pixels))
        }

        // When a player starts tracking a painted player, send them the full skin
        // so they're in sync without waiting for the next stroke.
        EntityTrackingEvents.START_TRACKING.register { tracked, viewer ->
            if (tracked is Player) {
                val image = ServerSkinStore.get(tracked.uuid) ?: return@register
                ServerPlayNetworking.send(viewer, SkinSnapshot(tracked.uuid, image.pixels))
            }
        }
    }

    /** Send to everyone who can see [painter] (not the painter themselves). */
    private fun broadcastToViewers(painter: ServerPlayer, payload: net.minecraft.network.protocol.common.custom.CustomPacketPayload) {
        for (viewer in PlayerLookup.tracking(painter)) {
            ServerPlayNetworking.send(viewer, payload)
        }
    }
}
