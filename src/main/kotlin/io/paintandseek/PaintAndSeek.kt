package io.paintandseek

import io.paintandseek.game.GameManager
import io.paintandseek.game.PaintAndSeekCommand
import io.paintandseek.item.PaintAndSeekItems
import io.paintandseek.net.PaintNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object PaintAndSeek : ModInitializer {
	const val MOD_ID: String = "paintandseek"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		PaintAndSeekItems.register()
		PaintNetworking.registerPayloads()
		PaintNetworking.registerServerHandlers()

		PaintAndSeekCommand.register()
		ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server -> GameManager.tick(server) })
		ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, _, _ ->
			GameManager.onEntityDamaged(entity, source)
		}

		LOGGER.info("paintandseek initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
