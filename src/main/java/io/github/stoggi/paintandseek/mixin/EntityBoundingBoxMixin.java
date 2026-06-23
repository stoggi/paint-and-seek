package io.github.stoggi.paintandseek.mixin;

import io.github.stoggi.paintandseek.game.PoseHitbox;
import io.github.stoggi.paintandseek.skin.ServerPoseStore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make a posed player's server-side collision/hit box track their pose, so posed
 * limbs and lying ground poses can't clip through walls and can actually be struck
 * by the seeker's spectral arrow. Poses are render-only otherwise (see the client
 * model mixins), leaving the server with the default upright column.
 *
 * Server-side players only; non-players and the client keep the vanilla box (the
 * pose store is server-authoritative and empty on the client anyway).
 */
@Mixin(Entity.class)
public class EntityBoundingBoxMixin {

	@Inject(
		method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void paintandseek$poseBoundingBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() || !(self instanceof Player player)) {
			return;
		}
		Integer poseId = ServerPoseStore.INSTANCE.get(player.getUUID());
		if (poseId == null) {
			return; // store never holds DEFAULT; null = no pose
		}
		AABB posed = PoseHitbox.INSTANCE.boxFor(player, position, poseId);
		if (posed != null) {
			cir.setReturnValue(posed);
		}
	}
}
