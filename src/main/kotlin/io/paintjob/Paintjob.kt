package io.paintjob

import io.paintjob.net.PaintNetworking
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Paintjob : ModInitializer {
	const val MOD_ID: String = "paintjob"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		PaintNetworking.registerPayloads()
		PaintNetworking.registerServerHandlers()
		LOGGER.info("paintjob initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
