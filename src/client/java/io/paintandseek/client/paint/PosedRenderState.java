package io.paintandseek.client.paint;

/**
 * Duck interface on the player render state so the captured pose travels from
 * render-state extraction (which has the entity) to the model's setupAnim.
 *
 * Lives outside the mixin package because non-mixin code references it.
 */
public interface PosedRenderState {
	void paintandseek$setPose(PaintPose pose);

	PaintPose paintandseek$getPose();

	/** True for the local player while painting: fully freeze the model to match the pick geometry. */
	void paintandseek$setFrozen(boolean frozen);

	boolean paintandseek$isFrozen();
}
