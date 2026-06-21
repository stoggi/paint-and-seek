package io.paintandseek.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.paintandseek.PaintAndSeek;
import io.paintandseek.client.paint.PaintPose;
import io.paintandseek.client.paint.PosedRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render a painted skin with the opaque, depth-writing CUTOUT render type instead
 * of the default TRANSLUCENT one. Translucent entities don't write depth, so
 * translucent world geometry (clouds, water) sorts on top of them; cutout writes
 * depth so it occludes correctly, while alpha-testing still hides the cleared
 * overlay texels. The skin is still lit normally (reacts to world light).
 *
 * Painted players are identified by their body texture being in our namespace
 * (swapped in by AbstractClientPlayerMixin).
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
	private void paintandseek$cutoutPaintedSkin(
		LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing,
		CallbackInfoReturnable<RenderType> cir
	) {
		if (cir.getReturnValue() == null || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		Identifier texture = avatar.skin.body().texturePath();
		if (texture.getNamespace().equals(PaintAndSeek.MOD_ID)) {
			cir.setReturnValue(RenderTypes.entityCutout(texture));
		}
	}

	/**
	 * Tip/lower the whole model for ground poses (starfish, lie-flat, ball, sit).
	 * setupRotations runs right after the body-yaw, in feet-origin block space, so
	 * rotating about X lays the model flat pivoting at the feet. Applied for posed
	 * players including the local one while painting, so the paint preview shows the
	 * pose; PaintRaycaster mirrors this exact transform so clicks stay aligned.
	 */
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void paintandseek$groundPose(
		LivingEntityRenderState state, PoseStack poseStack, float bob, float yRot, CallbackInfo ci
	) {
		if (!(state instanceof PosedRenderState posed)) {
			return;
		}
		PaintPose pose = posed.paintandseek$getPose();
		if (pose == null || !pose.getHasBodyTransform()) {
			return;
		}
		poseStack.translate(0.0F, pose.getBodyOffsetY(), pose.getBodyOffsetZ());
		if (pose.getBodyPitch() != 0f) {
			poseStack.mulPose(new Quaternionf().rotationX(pose.getBodyPitch()));
		}
	}
}
