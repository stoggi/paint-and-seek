package io.paintjob.client.mixin;

import io.paintjob.client.paint.LimbRot;
import io.paintjob.client.paint.PaintPose;
import io.paintjob.client.paint.PosedRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a player's selected pose to the rendered model. Uses the same limb
 * rotations as the pick geometry, so what you see is what you paint. Only player
 * render states carry a pose, so other humanoids are unaffected.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void paintjob$applyPose(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof PosedRenderState posed)) {
			return;
		}
		PaintPose pose = posed.paintjob$getPose();
		if (pose == null) {
			pose = PaintPose.DEFAULT;
		}
		boolean frozen = posed.paintjob$isFrozen();
		// Normal animation unless the player is posed or (locally) painting.
		if (!frozen && pose == PaintPose.DEFAULT) {
			return;
		}
		paintjob$pose(this.rightArm, pose.getRightArm());
		paintjob$pose(this.leftArm, pose.getLeftArm());
		paintjob$pose(this.rightLeg, pose.getRightLeg());
		paintjob$pose(this.leftLeg, pose.getLeftLeg());
		// Freeze the head to neutral so it matches the (static) pick geometry.
		this.head.xRot = 0f;
		this.head.yRot = 0f;
		this.head.zRot = 0f;
	}

	@Unique
	private void paintjob$pose(ModelPart part, LimbRot rot) {
		part.xRot = rot.getX();
		part.yRot = rot.getY();
		part.zRot = rot.getZ();
	}
}
