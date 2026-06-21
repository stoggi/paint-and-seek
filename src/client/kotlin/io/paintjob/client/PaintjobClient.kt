package io.paintjob.client

import io.paintjob.client.paint.ClientPoseStore
import io.paintjob.client.paint.PaintMode
import io.paintjob.client.paint.PaintPose
import io.paintjob.client.skin.PaintedSkinTextures
import io.paintjob.item.PaintjobItems
import io.paintjob.net.ClearSkin
import io.paintjob.net.PoseSync
import io.paintjob.net.SkinPatch
import io.paintjob.net.SkinSnapshot
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult

object PaintjobClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Payload types are registered once in the common entrypoint (which also runs
        // client-side); here we only add the client-bound receivers.
        registerClientReceivers()

        // Right-clicking the paint brush toggles paint mode (front third-person view).
        UseItemCallback.EVENT.register { player, level, hand ->
            if (level.isClientSide &&
                player === Minecraft.getInstance().player &&
                player.getItemInHand(hand).item === PaintjobItems.paintBrush
            ) {
                PaintMode.toggle()
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }

        // Don't leak GPU textures across server hops; also leave paint mode.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            PaintMode.restoreCamera()
            PaintedSkinTextures.clearAll()
            ClientPoseStore.clearAll()
        }
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
        ClientPlayNetworking.registerGlobalReceiver(PoseSync.TYPE) { payload, _ ->
            ClientPoseStore.set(payload.uuid, PaintPose.byId(payload.poseId))
        }
    }
}
