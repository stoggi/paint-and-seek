package io.paintjob.client.mixin;

import io.paintjob.client.paint.PaintPose;

/**
 * Duck interface on the player render state so the captured pose travels from
 * render-state extraction (which has the entity) to the model's setupAnim.
 */
public interface PosedRenderState {
	void paintjob$setPose(PaintPose pose);

	PaintPose paintjob$getPose();
}
