package io.paintjob.client

import io.paintjob.client.skin.PaintedSkinTextures
import io.paintjob.net.ClearSkin
import io.paintjob.net.SkinPatch
import io.paintjob.net.SkinSnapshot
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

object PaintjobClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Payload types are registered once in the common entrypoint (which also runs
        // client-side); here we only add the client-bound receivers.
        registerClientReceivers()

        // Don't leak GPU textures across server hops.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> PaintedSkinTextures.clearAll() }
    }

    private fun registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SkinSnapshot.TYPE) { payload, _ ->
            PaintedSkinTextures.applySnapshot(payload.uuid, payload.pixels)
        }
        ClientPlayNetworking.registerGlobalReceiver(SkinPatch.TYPE) { payload, _ ->
            PaintedSkinTextures.applyPatch(payload.uuid, payload.rect)
        }
        ClientPlayNetworking.registerGlobalReceiver(ClearSkin.TYPE) { payload, _ ->
            PaintedSkinTextures.clear(payload.uuid)
        }
    }
}
