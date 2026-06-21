package io.paintjob.client.mixin;

import io.paintjob.client.paint.ClientPoseStore;
import io.paintjob.client.paint.PaintMode;
import io.paintjob.client.paint.PaintState;
import io.paintjob.client.paint.PosedRenderState;
import io.paintjob.client.skin.PaintedSkinTextures;
import net.minecraft.client.Minecraft;
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
		// The local player painting is fully frozen to match the pick geometry;
		// everyone else just shows their networked pose.
		boolean local = entity == Minecraft.getInstance().player && PaintMode.INSTANCE.getActive();
		((PosedRenderState) state).paintjob$setFrozen(local);
		((PosedRenderState) state).paintjob$setPose(
			local ? PaintState.INSTANCE.getPose() : ClientPoseStore.INSTANCE.get(entity.getUUID())
		);

		// Render painted skins fullbright so the painted colour shows exactly as it
		// was eye-dropped (no second round of world lighting).
		if (PaintedSkinTextures.INSTANCE.has(entity.getUUID())) {
			state.lightCoords = 0xF000F0;
		}
	}
}
