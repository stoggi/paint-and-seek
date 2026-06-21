package io.paintjob.game

import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

/**
 * `/paintjob newround [seeker]` and `/paintjob endround` (operators only).
 */
object PaintjobCommand {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("paintjob")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(
                        Commands.literal("newround")
                            .executes { ctx -> newRound(ctx, null) }
                            .then(
                                Commands.argument("seeker", EntityArgument.player())
                                    .executes { ctx -> newRound(ctx, EntityArgument.getPlayer(ctx, "seeker")) },
                            ),
                    )
                    .then(
                        Commands.literal("endround")
                            .executes { ctx -> endRound(ctx) },
                    ),
            )
        }
    }

    private fun newRound(ctx: CommandContext<CommandSourceStack>, seeker: ServerPlayer?): Int {
        val result = GameManager.newRound(ctx.source.server, seeker)
        ctx.source.sendSuccess({ result }, true)
        return 1
    }

    private fun endRound(ctx: CommandContext<CommandSourceStack>): Int {
        val result = GameManager.endRound(ctx.source.server)
        ctx.source.sendSuccess({ result }, true)
        return 1
    }
}
