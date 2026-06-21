package io.paintjob.client.paint;

/**
 * Duck interface on the player render state so the captured pose travels from
 * render-state extraction (which has the entity) to the model's setupAnim.
 *
 * Lives outside the mixin package because non-mixin code references it.
 */
public interface PosedRenderState {
	void paintjob$setPose(PaintPose pose);

	PaintPose paintjob$getPose();
}
