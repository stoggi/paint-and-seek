package io.paintjob.client.mixin;

import io.paintjob.client.paint.ClientPoseStore;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures each player's networked pose onto their render state each frame. */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void paintjob$capturePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		((PosedRenderState) state).paintjob$setPose(ClientPoseStore.INSTANCE.get(entity.getUUID()));
	}
}
