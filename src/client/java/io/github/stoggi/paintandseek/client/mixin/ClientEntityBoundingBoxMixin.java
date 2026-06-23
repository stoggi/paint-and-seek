package io.github.stoggi.paintandseek.client.mixin;

import io.github.stoggi.paintandseek.client.paint.ClientPoseStore;
import io.github.stoggi.paintandseek.game.PoseHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side twin of the server's pose-matched bounding box (EntityBoundingBoxMixin):
 * gives posed players the same snug, yaw-oriented box on the client so the F3+B debug
 * box and local movement prediction match what the server uses for hits/collision.
 *
 * The server is authoritative for arrow hits; this keeps the client consistent. Pose
 * comes from ClientPoseStore (every networked pose, including the local player's own,
 * which PaintScreen records locally). The server's mixin guards to the server side and
 * this one to the client, so each acts only on its own side.
 */
@Mixin(Entity.class)
public class ClientEntityBoundingBoxMixin {

	@Inject(
		method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void paintandseek$clientPoseBoundingBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
		Entity self = (Entity) (Object) this;
		if (!self.level().isClientSide() || !(self instanceof Player player)) {
			return;
		}
		int poseId = ClientPoseStore.INSTANCE.get(player.getUUID()).ordinal();
		AABB posed = PoseHitbox.INSTANCE.boxFor(player, position, poseId);
		if (posed != null) {
			cir.setReturnValue(posed);
		}
	}
}
