package io.paintjob.client.mixin;

import io.paintjob.client.paint.PaintPose;
import io.paintjob.client.paint.PosedRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the player's selected pose on their render state. */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PosedRenderState {

	@Unique
	private PaintPose paintjob$pose = PaintPose.DEFAULT;

	@Unique
	private boolean paintjob$frozen = false;

	@Override
	public void paintjob$setPose(PaintPose pose) {
		this.paintjob$pose = pose;
	}

	@Override
	public PaintPose paintjob$getPose() {
		return this.paintjob$pose;
	}

	@Override
	public void paintjob$setFrozen(boolean frozen) {
		this.paintjob$frozen = frozen;
	}

	@Override
	public boolean paintjob$isFrozen() {
		return this.paintjob$frozen;
	}
}
