package io.paintjob.game

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.TeamColor
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.Optional
import java.util.UUID
import kotlin.math.cos

/**
 * Server-side hide-and-seek round controller (simple, single-round form).
 *
 * `newRound` picks a seeker (named or random), makes everyone else a hider,
 * announces roles, hides hider nametags, and shows a sidebar scoreboard. Each
 * tick a hider that sits in the seeker's view cone (with line of sight) gains
 * points. A hider that punches the seeker is "locked": scoring stops and their
 * scoreboard entry turns red. `endRound` announces the winner and clears up.
 */
object GameManager {
    private const val OBJECTIVE_NAME = "paintjob"
    private const val HIDER_TEAM = "pj_hiders"
    private const val LOCKED_TEAM = "pj_locked"

    private const val FOV_HALF_ANGLE_DEG = 35.0 // ~70° cone
    private const val MAX_RANGE = 64.0
    private const val TICKS_PER_POINT = 20 // 1 point per second in view

    private val fovCosThreshold = cos(Math.toRadians(FOV_HALF_ANGLE_DEG))

    private class HiderState(val name: String) {
        var score = 0
        var locked = false
        var viewTicks = 0
    }

    private var active = false
    private var seekerId: UUID? = null
    private var seekerName: String = ""
    private val hiders = LinkedHashMap<UUID, HiderState>()

    val isActive: Boolean get() = active

    fun newRound(server: MinecraftServer, chosenSeeker: ServerPlayer?): Component {
        clear(server)

        val players = server.playerList.players
        if (players.isEmpty()) return msg("No players online to start a round.", ChatFormatting.RED)

        val seeker = chosenSeeker ?: players.random()
        seekerId = seeker.uuid
        seekerName = seeker.scoreboardName

        val scoreboard = server.scoreboard
        val objective = scoreboard.addObjective(
            OBJECTIVE_NAME,
            ObjectiveCriteria.DUMMY,
            msg("Paintjob", ChatFormatting.AQUA),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            null,
        )
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective)

        val hiderTeam = scoreboard.addPlayerTeam(HIDER_TEAM).apply {
            nameTagVisibility = Team.Visibility.NEVER
        }
        scoreboard.addPlayerTeam(LOCKED_TEAM).apply {
            nameTagVisibility = Team.Visibility.NEVER
            color = Optional.of(TeamColor.RED)
        }

        for (player in players) {
            if (player.uuid == seekerId) continue
            hiders[player.uuid] = HiderState(player.scoreboardName)
            scoreboard.addPlayerToTeam(player.scoreboardName, hiderTeam)
            scoreboard.getOrCreatePlayerScore(player, objective).set(0)
        }

        active = true
        showRoleTitles(server)

        server.playerList.broadcastSystemMessage(
            msg("Paintjob round started — seeker is ", ChatFormatting.AQUA)
                .append(msg(seekerName, ChatFormatting.RED)),
            false,
        )
        return msg("Round started. Seeker: $seekerName, hiders: ${hiders.size}", ChatFormatting.GREEN)
    }

    fun endRound(server: MinecraftServer): Component {
        if (!active) return msg("No round is active.", ChatFormatting.RED)

        val winner = hiders.values.maxByOrNull { it.score }
        val announcement = if (winner != null) {
            msg("Paintjob round over — winner: ", ChatFormatting.AQUA)
                .append(msg(winner.name, ChatFormatting.GREEN))
                .append(msg(" with ${winner.score} points!", ChatFormatting.AQUA))
        } else {
            msg("Paintjob round over — no hiders.", ChatFormatting.AQUA)
        }
        server.playerList.broadcastSystemMessage(announcement, false)

        clear(server)
        return msg("Round ended.", ChatFormatting.GREEN)
    }

    /** Called when a player attacks an entity; locks a hider that punches the seeker. */
    fun onPlayerAttack(attacker: Player, target: Entity): Boolean {
        if (!active) return false
        val state = hiders[attacker.uuid] ?: return false
        if (target.uuid != seekerId || state.locked) return false

        state.locked = true
        val server = attacker.level().server ?: return true
        val scoreboard = server.scoreboard
        scoreboard.getPlayerTeam(LOCKED_TEAM)?.let { scoreboard.addPlayerToTeam(state.name, it) }
        server.playerList.broadcastSystemMessage(
            msg(state.name, ChatFormatting.RED).append(msg(" tagged the seeker — score locked.", ChatFormatting.GRAY)),
            false,
        )
        return true // caught the seeker; cancel the damage
    }

    fun tick(server: MinecraftServer) {
        if (!active) return
        val seeker = seekerId?.let { server.playerList.getPlayer(it) } ?: return
        val eye = seeker.eyePosition
        val look = seeker.getViewVector(1.0f)
        val objective = server.scoreboard.getObjective(OBJECTIVE_NAME) ?: return

        for ((uuid, state) in hiders) {
            if (state.locked) continue
            val hider = server.playerList.getPlayer(uuid) ?: continue
            if (hider.level() !== seeker.level()) continue

            val toHider = hider.eyePosition.subtract(eye)
            val dist = toHider.length()
            if (dist > MAX_RANGE || dist < 1.0e-4) continue
            if (toHider.scale(1.0 / dist).dot(look) < fovCosThreshold) continue // outside cone
            if (isObstructed(seeker, eye, hider.eyePosition)) continue

            state.viewTicks++
            if (state.viewTicks % TICKS_PER_POINT == 0) {
                state.score++
                server.scoreboard.getOrCreatePlayerScore(hider, objective).set(state.score)
            }
        }
    }

    private fun isObstructed(seeker: ServerPlayer, from: net.minecraft.world.phys.Vec3, to: net.minecraft.world.phys.Vec3): Boolean {
        val hit = seeker.level().clip(
            ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, seeker),
        )
        return hit.type == HitResult.Type.BLOCK
    }

    private fun showRoleTitles(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val title = if (player.uuid == seekerId) {
                msg("SEEKING", ChatFormatting.RED, bold = true)
            } else {
                msg("HIDING", ChatFormatting.GREEN, bold = true)
            }
            player.connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20)) // ~5s
            player.connection.send(ClientboundSetTitleTextPacket(title))
        }
    }

    private fun clear(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        scoreboard.getObjective(OBJECTIVE_NAME)?.let { scoreboard.removeObjective(it) }
        scoreboard.getPlayerTeam(HIDER_TEAM)?.let { scoreboard.removePlayerTeam(it) }
        scoreboard.getPlayerTeam(LOCKED_TEAM)?.let { scoreboard.removePlayerTeam(it) }
        active = false
        seekerId = null
        seekerName = ""
        hiders.clear()
    }

    private fun msg(text: String, color: ChatFormatting, bold: Boolean = false): net.minecraft.network.chat.MutableComponent {
        val c = Component.literal(text).withStyle(color)
        return if (bold) c.withStyle(ChatFormatting.BOLD) else c
    }
}
