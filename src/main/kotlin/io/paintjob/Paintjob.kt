package io.paintjob

import io.paintjob.game.GameManager
import io.paintjob.game.PaintjobCommand
import io.paintjob.item.PaintjobItems
import io.paintjob.net.PaintNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import org.slf4j.LoggerFactory

object Paintjob : ModInitializer {
	const val MOD_ID: String = "paintjob"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		PaintjobItems.register()
		PaintNetworking.registerPayloads()
		PaintNetworking.registerServerHandlers()

		PaintjobCommand.register()
		ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server -> GameManager.tick(server) })
		AttackEntityCallback.EVENT.register { player, world, _, entity, _ ->
			if (!world.isClientSide && GameManager.onPlayerAttack(player, entity)) {
				InteractionResult.FAIL // hider tagged the seeker — cancel the damage
			} else {
				InteractionResult.PASS
			}
		}

		LOGGER.info("paintjob initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
