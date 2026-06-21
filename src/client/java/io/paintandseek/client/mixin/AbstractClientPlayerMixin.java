package io.paintandseek.client.mixin;

import io.paintandseek.client.skin.PaintedSkinTextures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects a player's body texture to their painted skin when one exists.
 *
 * {@link AbstractClientPlayer#getSkin()} is read every frame by AvatarRenderer's
 * render-state extraction, and the bound texture is {@code skin.body().texturePath()},
 * so swapping the body here makes painted skins (and live strokes) render immediately.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void paintandseek$overridePaintedSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		Identifier paintedId = PaintedSkinTextures.INSTANCE.textureId(self.getUUID());
		if (paintedId == null) {
			return;
		}
		PlayerSkin original = cir.getReturnValue();
		ClientAsset.Texture paintedBody = new ClientAsset.DownloadedTexture(paintedId, "paintandseek");
		cir.setReturnValue(new PlayerSkin(
			paintedBody,
			original.cape(),
			original.elytra(),
			original.model(),
			original.secure()
		));
	}
}
