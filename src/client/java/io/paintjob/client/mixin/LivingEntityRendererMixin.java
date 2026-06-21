package io.paintjob.client.mixin;

import io.paintjob.Paintjob;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render a painted skin emissive (unlit) so the painted colour displays exactly
 * as it was eye-dropped, at any time of day. (Plain "fullbright" still samples
 * the time-of-day lightmap, which tints colours at night; emissive ignores it.)
 *
 * Painted players are identified by their body texture being in our namespace
 * (swapped in by AbstractClientPlayerMixin).
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
	private void paintjob$emissivePaintedSkin(
		LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing,
		CallbackInfoReturnable<RenderType> cir
	) {
		if (cir.getReturnValue() == null || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		Identifier texture = avatar.skin.body().texturePath();
		if (texture.getNamespace().equals(Paintjob.MOD_ID)) {
			cir.setReturnValue(RenderTypes.entityTranslucentEmissive(texture));
		}
	}
}
