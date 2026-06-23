package io.github.stoggi.paintandseek.client

import io.github.stoggi.paintandseek.client.paint.ClientPoseHitbox
import io.github.stoggi.paintandseek.client.paint.ClientPoseStore
import io.github.stoggi.paintandseek.client.paint.PaintMode
import io.github.stoggi.paintandseek.client.paint.PaintPose
import io.github.stoggi.paintandseek.client.skin.PaintedSkinTextures
import io.github.stoggi.paintandseek.item.PaintAndSeekItems
import io.github.stoggi.paintandseek.net.ClearSkin
import io.github.stoggi.paintandseek.net.PoseSync
import io.github.stoggi.paintandseek.net.SkinPatch
import io.github.stoggi.paintandseek.net.SkinSnapshot
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult

object PaintAndSeekClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Payload types are registered once in the common entrypoint (which also runs
        // client-side); here we only add the client-bound receivers.
        registerClientReceivers()

        // Right-clicking the paint brush toggles paint mode (front third-person view).
        UseItemCallback.EVENT.register { player, level, hand ->
            if (level.isClientSide &&
                player === Minecraft.getInstance().player &&
                player.getItemInHand(hand).item === PaintAndSeekItems.paintBrush
            ) {
                PaintMode.toggle()
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }

        // Keep posed players' client bounding box in sync with their pose/yaw.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { ClientPoseHitbox.tick() })

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
