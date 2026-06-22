package io.github.stoggi.paintandseek.game

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

/**
 * `/paintandseek newround [seeker] [hideTime] [seekTime]` and `/paintandseek endround`
 * (operators only). Times are in seconds; defaults 120 (hide) / 300 (seek).
 */
object PaintAndSeekCommand {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("paintandseek")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(
                        Commands.literal("newround")
                            .executes { ctx -> newRound(ctx, null, GameManager.DEFAULT_HIDE_SECONDS, GameManager.DEFAULT_SEEK_SECONDS, GameManager.DEFAULT_ARROWS) }
                            .then(
                                Commands.argument("seeker", EntityArgument.player())
                                    .executes { ctx -> newRound(ctx, seekerOf(ctx), GameManager.DEFAULT_HIDE_SECONDS, GameManager.DEFAULT_SEEK_SECONDS, GameManager.DEFAULT_ARROWS) }
                                    .then(
                                        Commands.argument("hideTime", IntegerArgumentType.integer(0))
                                            .executes { ctx -> newRound(ctx, seekerOf(ctx), intOf(ctx, "hideTime"), GameManager.DEFAULT_SEEK_SECONDS, GameManager.DEFAULT_ARROWS) }
                                            .then(
                                                Commands.argument("seekTime", IntegerArgumentType.integer(1))
                                                    .executes { ctx -> newRound(ctx, seekerOf(ctx), intOf(ctx, "hideTime"), intOf(ctx, "seekTime"), GameManager.DEFAULT_ARROWS) }
                                                    .then(
                                                        Commands.argument("arrows", IntegerArgumentType.integer(0))
                                                            .executes { ctx -> newRound(ctx, seekerOf(ctx), intOf(ctx, "hideTime"), intOf(ctx, "seekTime"), intOf(ctx, "arrows")) },
                                                    ),
                                            ),
                                    ),
                            ),
                    )
                    .then(
                        Commands.literal("endround")
                            .executes { ctx -> endRound(ctx) },
                    ),
            )
        }
    }

    private fun seekerOf(ctx: CommandContext<CommandSourceStack>) = EntityArgument.getPlayer(ctx, "seeker")
    private fun intOf(ctx: CommandContext<CommandSourceStack>, name: String) = IntegerArgumentType.getInteger(ctx, name)

    private fun newRound(ctx: CommandContext<CommandSourceStack>, seeker: ServerPlayer?, hideTime: Int, seekTime: Int, arrows: Int): Int {
        // Run outside this command's execution context so the round's hook commands
        // (tag/function) execute top-level and synchronously, in the right order.
        val source = ctx.source
        val server = source.server
        server.execute {
            val result = GameManager.newRound(server, seeker, hideTime, seekTime, arrows)
            source.sendSuccess({ result }, true)
        }
        return 1
    }

    private fun endRound(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        server.execute {
            val result = GameManager.endRound(server)
            source.sendSuccess({ result }, true)
        }
        return 1
    }
}
