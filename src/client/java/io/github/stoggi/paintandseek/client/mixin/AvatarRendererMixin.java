package io.github.stoggi.paintandseek.client.mixin;

import io.github.stoggi.paintandseek.client.paint.ClientPoseStore;
import io.github.stoggi.paintandseek.client.paint.PaintMode;
import io.github.stoggi.paintandseek.client.paint.PaintState;
import io.github.stoggi.paintandseek.client.paint.PosedRenderState;
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
	private void paintandseek$capturePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		// The local player painting is fully frozen to match the pick geometry;
		// everyone else just shows their networked pose.
		boolean local = entity == Minecraft.getInstance().player && PaintMode.INSTANCE.getActive();
		((PosedRenderState) state).paintandseek$setFrozen(local);
		((PosedRenderState) state).paintandseek$setPose(
			local ? PaintState.INSTANCE.getPose() : ClientPoseStore.INSTANCE.get(entity.getUUID())
		);
	}
}
