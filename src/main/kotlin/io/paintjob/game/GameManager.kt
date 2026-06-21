package io.paintjob.game

import io.paintjob.Paintjob
import io.paintjob.item.PaintjobItems
import io.paintjob.net.ClearSkin
import io.paintjob.net.PoseSync
import io.paintjob.skin.ServerPoseStore
import io.paintjob.skin.ServerSkinStore
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.waypoints.WaypointTransmitter
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.SpectralArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.TeamColor
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.Optional
import java.util.UUID
import kotlin.math.cos

/**
 * Server-side hide-and-seek round controller.
 *
 * A round has a HIDE phase (hiders get paint brushes and go disguise themselves;
 * no scoring) then a SEEK phase (the seeker hunts; hiders in the seeker's view
 * cone + line of sight score points). A hider that punches the seeker is "found"
 * (locked: red on the scoreboard, scoring stops). The round ends when every hider
 * is found (seeker wins) or the seek timer runs out with survivors (hiders win).
 */
object GameManager {
    const val DEFAULT_HIDE_SECONDS = 120
    const val DEFAULT_SEEK_SECONDS = 300
    const val DEFAULT_ARROWS = 20

    private const val OBJECTIVE_NAME = "paintjob"
    private const val HIDER_TEAM = "pj_hiders"
    private const val LOCKED_TEAM = "pj_locked"

    private const val FOV_HALF_ANGLE_DEG = 35.0 // ~70° cone
    private const val MAX_RANGE = 64.0
    private const val TICKS_PER_POINT = 20 // 1 point per second in view

    private val fovCosThreshold = cos(Math.toRadians(FOV_HALF_ANGLE_DEG))

    private enum class Phase { NONE, HIDE, SEEK }

    private class HiderState(val name: String) {
        var score = 0
        var locked = false
        var viewTicks = 0
    }

    private var phase = Phase.NONE
    private var seekerId: UUID? = null
    private var seekerName: String = ""
    private val hiders = LinkedHashMap<UUID, HiderState>()
    private var hideTicksLeft = 0
    private var seekTicksLeft = 0
    private var displayCounter = 0

    val isActive: Boolean get() = phase != Phase.NONE

    fun newRound(server: MinecraftServer, chosenSeeker: ServerPlayer?, hideSeconds: Int, seekSeconds: Int, arrows: Int): Component {
        revertSkins(server) // reset last round's painted skins/poses only when a new round begins
        for (player in server.playerList.players) player.removeEffect(MobEffects.GLOWING) // clear survivor glow
        clear(server)

        val players = server.playerList.players
        if (players.isEmpty()) return msg("No players online to start a round.", ChatFormatting.RED)

        val seeker = chosenSeeker ?: players.random()
        seekerId = seeker.uuid
        seekerName = seeker.scoreboardName

        val scoreboard = server.scoreboard
        val objective = scoreboard.addObjective(
            OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, msg("Paintjob", ChatFormatting.AQUA),
            ObjectiveCriteria.RenderType.INTEGER, false, null,
        )
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective)

        val hiderTeam = scoreboard.addPlayerTeam(HIDER_TEAM).apply { nameTagVisibility = Team.Visibility.NEVER }
        scoreboard.addPlayerTeam(LOCKED_TEAM).apply {
            nameTagVisibility = Team.Visibility.NEVER
            color = Optional.of(TeamColor.RED)
        }

        for (player in players) {
            if (player.uuid == seekerId) continue
            hiders[player.uuid] = HiderState(player.scoreboardName)
            scoreboard.addPlayerToTeam(player.scoreboardName, hiderTeam)
            scoreboard.getOrCreatePlayerScore(player, objective).set(0)
            player.inventory.add(ItemStack(PaintjobItems.paintBrush)) // hiders get a brush
        }
        // The seeker gets a brush, plus a bow and spectral arrows to tag hiders.
        seeker.inventory.add(ItemStack(PaintjobItems.paintBrush))
        seeker.inventory.add(ItemStack(Items.BOW))
        if (arrows > 0) seeker.inventory.add(ItemStack(Items.SPECTRAL_ARROW, arrows))

        // Hide every participant from the locator bar so positions aren't given away.
        hideLocator(seeker)
        for (uuid in hiders.keys) server.playerList.getPlayer(uuid)?.let { hideLocator(it) }

        phase = Phase.HIDE
        hideTicksLeft = hideSeconds * 20
        seekTicksLeft = seekSeconds * 20
        displayCounter = 0
        updateTimerDisplay(server)
        showRoleTitles(server)

        server.playerList.broadcastSystemMessage(
            msg("Paintjob — seeker is ", ChatFormatting.AQUA).append(msg(seekerName, ChatFormatting.RED))
                .append(msg(". Hiders have ${hideSeconds}s to hide and paint!", ChatFormatting.AQUA)),
            false,
        )
        return msg("Round started. Seeker: $seekerName, hiders: ${hiders.size}", ChatFormatting.GREEN)
    }

    fun endRound(server: MinecraftServer): Component {
        if (phase == Phase.NONE) return msg("No round is active.", ChatFormatting.RED)

        val hasHiders = hiders.isNotEmpty()
        val allFound = hasHiders && hiders.values.all { it.locked }
        val announcement = when {
            !hasHiders -> msg("Paintjob round over — no hiders.", ChatFormatting.AQUA)
            allFound -> msg("All hiders found — ", ChatFormatting.AQUA)
                .append(msg("SEEKER WINS! ($seekerName)", ChatFormatting.RED, bold = true))
            else -> {
                val survivors = hiders.values.count { !it.locked }
                // Surviving hiders glow for 120s (cleared on new round).
                for ((uuid, state) in hiders) {
                    if (!state.locked) {
                        server.playerList.getPlayer(uuid)?.addEffect(MobEffectInstance(MobEffects.GLOWING, 120 * 20))
                    }
                }
                msg("Time's up — ", ChatFormatting.AQUA)
                    .append(msg("HIDERS WIN! ", ChatFormatting.GREEN, bold = true))
                    .append(msg("$survivors survived.", ChatFormatting.AQUA))
            }
        }
        server.playerList.broadcastSystemMessage(announcement, false)

        clear(server)
        return msg("Round ended.", ChatFormatting.GREEN)
    }

    /** A hider is found only when struck by the seeker's spectral arrow. */
    fun onEntityDamaged(victim: LivingEntity, source: DamageSource) {
        if (phase == Phase.NONE) return
        if (source.directEntity !is SpectralArrow) return
        val state = hiders[victim.uuid] ?: return
        if (state.locked) return
        val server = victim.level().server ?: return

        state.locked = true
        server.scoreboard.getPlayerTeam(LOCKED_TEAM)?.let { server.scoreboard.addPlayerToTeam(state.name, it) }
        server.playerList.broadcastSystemMessage(
            msg(state.name, ChatFormatting.RED).append(msg(" was found!", ChatFormatting.GRAY)),
            false,
        )
    }

    fun tick(server: MinecraftServer) {
        when (phase) {
            Phase.NONE -> return
            Phase.HIDE -> {
                if (--hideTicksLeft <= 0) startSeek(server)
            }
            Phase.SEEK -> {
                seekerId?.let { server.playerList.getPlayer(it) }?.let { doScoring(server, it) }
                if (hiders.isNotEmpty() && hiders.values.all { it.locked }) {
                    endRound(server)
                    return
                }
                if (--seekTicksLeft <= 0) {
                    endRound(server)
                    return
                }
            }
        }
        if (displayCounter++ % 20 == 0) updateTimerDisplay(server)
    }

    private fun startSeek(server: MinecraftServer) {
        phase = Phase.SEEK
        server.playerList.broadcastSystemMessage(msg("Seek time — find the hiders!", ChatFormatting.GOLD), false)
        for (player in server.playerList.players) sendTitle(player, msg("SEEK!", ChatFormatting.GOLD, bold = true))
        updateTimerDisplay(server)
    }

    private fun doScoring(server: MinecraftServer, seeker: ServerPlayer) {
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
            if (toHider.scale(1.0 / dist).dot(look) < fovCosThreshold) continue
            if (isObstructed(seeker, eye, hider.eyePosition)) continue

            state.viewTicks++
            if (state.viewTicks % TICKS_PER_POINT == 0) {
                state.score++
                server.scoreboard.getOrCreatePlayerScore(hider, objective).set(state.score)
            }
        }
    }

    private fun isObstructed(seeker: ServerPlayer, from: Vec3, to: Vec3): Boolean {
        val hit = seeker.level().clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, seeker))
        return hit.type == HitResult.Type.BLOCK
    }

    private fun updateTimerDisplay(server: MinecraftServer) {
        val objective = server.scoreboard.getObjective(OBJECTIVE_NAME) ?: return
        val label = when (phase) {
            Phase.HIDE -> msg("Hide ", ChatFormatting.AQUA).append(msg(formatTime(hideTicksLeft), ChatFormatting.WHITE))
            Phase.SEEK -> msg("Seek ", ChatFormatting.GOLD).append(msg(formatTime(seekTicksLeft), ChatFormatting.WHITE))
            Phase.NONE -> return
        }
        objective.setDisplayName(msg("Paintjob ", ChatFormatting.AQUA).append(label))
    }

    private fun formatTime(ticks: Int): String {
        val total = (ticks / 20).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun showRoleTitles(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val title = if (player.uuid == seekerId) {
                msg("SEEKING", ChatFormatting.RED, bold = true)
            } else {
                msg("HIDING", ChatFormatting.GREEN, bold = true)
            }
            sendTitle(player, title)
        }
    }

    private fun sendTitle(player: ServerPlayer, title: Component) {
        player.connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20)) // ~5s
        player.connection.send(ClientboundSetTitleTextPacket(title))
    }

    /** Drop every participant's painted skin so they revert to their real skin. */
    private fun revertSkins(server: MinecraftServer) {
        val participants = buildList {
            seekerId?.let { add(it) }
            addAll(hiders.keys)
        }
        if (participants.isEmpty()) return
        for (uuid in participants) {
            ServerSkinStore.clear(uuid)
            ServerPoseStore.clear(uuid)
        }
        for (viewer in server.playerList.players) {
            for (uuid in participants) {
                ServerPlayNetworking.send(viewer, ClearSkin(uuid))
                ServerPlayNetworking.send(viewer, PoseSync(uuid, 0)) // 0 = DEFAULT pose
            }
        }
    }

    private val LOCATOR_HIDE_ID = Paintjob.id("locator_hidden")

    /** Zero a player's waypoint-transmit range so they vanish from the locator bar. */
    private fun hideLocator(player: ServerPlayer) {
        val attr = player.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE) ?: return
        attr.addTransientModifier(
            AttributeModifier(LOCATOR_HIDE_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        )
        player.level().waypointManager.untrackWaypoint(player as WaypointTransmitter)
    }

    private fun revealLocator(player: ServerPlayer) {
        val attr = player.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE) ?: return
        if (attr.removeModifier(LOCATOR_HIDE_ID)) {
            player.level().waypointManager.trackWaypoint(player as WaypointTransmitter)
        }
    }

    private fun removeBrushes(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val inv = player.inventory
            for (i in 0 until inv.containerSize) {
                if (inv.getItem(i).item === PaintjobItems.paintBrush) inv.setItem(i, ItemStack.EMPTY)
            }
        }
    }

    private fun clear(server: MinecraftServer) {
        removeBrushes(server)
        // Re-show participants on the locator bar.
        seekerId?.let { server.playerList.getPlayer(it) }?.let { revealLocator(it) }
        for (uuid in hiders.keys) server.playerList.getPlayer(uuid)?.let { revealLocator(it) }
        val scoreboard = server.scoreboard
        scoreboard.getObjective(OBJECTIVE_NAME)?.let { scoreboard.removeObjective(it) }
        scoreboard.getPlayerTeam(HIDER_TEAM)?.let { scoreboard.removePlayerTeam(it) }
        scoreboard.getPlayerTeam(LOCKED_TEAM)?.let { scoreboard.removePlayerTeam(it) }
        phase = Phase.NONE
        seekerId = null
        seekerName = ""
        hiders.clear()
    }

    private fun msg(text: String, color: ChatFormatting, bold: Boolean = false): MutableComponent {
        val c = Component.literal(text).withStyle(color)
        return if (bold) c.withStyle(ChatFormatting.BOLD) else c
    }
}
