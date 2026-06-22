package io.github.stoggi.paintandseek.client.mixin;

import io.github.stoggi.paintandseek.client.paint.PaintPose;
import io.github.stoggi.paintandseek.client.paint.PosedRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the player's selected pose on their render state. */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PosedRenderState {

	@Unique
	private PaintPose paintandseek$pose = PaintPose.DEFAULT;

	@Unique
	private boolean paintandseek$frozen = false;

	@Override
	public void paintandseek$setPose(PaintPose pose) {
		this.paintandseek$pose = pose;
	}

	@Override
	public PaintPose paintandseek$getPose() {
		return this.paintandseek$pose;
	}

	@Override
	public void paintandseek$setFrozen(boolean frozen) {
		this.paintandseek$frozen = frozen;
	}

	@Override
	public boolean paintandseek$isFrozen() {
		return this.paintandseek$frozen;
	}
}
