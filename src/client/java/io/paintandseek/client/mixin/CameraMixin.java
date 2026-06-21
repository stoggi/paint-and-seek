package io.paintandseek.client.mixin;

import io.paintandseek.client.paint.PaintCamera;
import io.paintandseek.client.paint.PaintMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While paint mode is active, override the camera placement to orbit around the
 * player at a controllable yaw/pitch/distance (see {@link PaintCamera}). Injected
 * at the tail of {@code alignWithEntity} so the frustum/projection downstream use
 * our camera, and so the raycaster (which reads the live camera) stays accurate.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void paintandseek$orbitWhilePainting(float partialTicks, CallbackInfo ci) {
		if (!PaintMode.INSTANCE.getActive()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		this.setRotation(PaintCamera.INSTANCE.getYaw(), PaintCamera.INSTANCE.getPitch());

		Vector3fc forward = ((Camera) (Object) this).forwardVector();
		double targetX = Mth.lerp(partialTicks, player.xo, player.getX());
		double targetY = Mth.lerp(partialTicks, player.yo, player.getY()) + 1.0; // body centre
		double targetZ = Mth.lerp(partialTicks, player.zo, player.getZ());
		double distance = PaintCamera.INSTANCE.getDistance();

		this.setPosition(
			targetX - forward.x() * distance,
			targetY - forward.y() * distance,
			targetZ - forward.z() * distance
		);
	}
}
